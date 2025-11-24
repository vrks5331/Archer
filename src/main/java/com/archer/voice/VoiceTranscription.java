package com.archer.voice;

import javax.sound.sampled.*;
import java.util.function.BiConsumer;
import org.vosk.Model;
import org.vosk.Recognizer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class VoiceTranscription {
    private static final float SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    private static final double SILENCE_THRESHOLD = 0.015; // Slightly higher to reduce false positives
    private static final int SILENCE_DURATION_MS = 3000; // 3 seconds of silence
    private static final int MIN_SPEECH_DURATION_MS = 500; // Minimum speech before considering silence
    
    private TargetDataLine targetLine;
    private Model model;
    private volatile boolean isListening = false;
    private Thread listeningThread;

    public VoiceTranscription() throws Exception {
        String modelPath = "C:\\Users\\srive\\vosk-model-small-en-us-0.15\\vosk-model-small-en-us-0.15";
        System.out.println("VoiceTranscription: Loading Vosk model from: " + modelPath);
        try {
            model = new Model(modelPath);
            System.out.println("VoiceTranscription: Model loaded successfully");
        } catch (Exception e) {
            System.err.println("VoiceTranscription: ERROR loading model: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }

    public synchronized void startListening(BiConsumer<String, Boolean> callback) {
        // Prevent multiple simultaneous listening sessions
        if (isListening) {
            System.out.println("VoiceTranscription: Already listening, ignoring start request");
            return;
        }
        
        isListening = true;
        listeningThread = new Thread(() -> {
            try {
                System.out.println("Starting voice listening session...");
                
                AudioFormat targetFormat = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                TargetDataLine line = null;
                
                // Try to open microphone with target format
                Mixer.Info[] mixers = AudioSystem.getMixerInfo();
                for (Mixer.Info mixerInfo : mixers) {
                    try {
                        Mixer mixer = AudioSystem.getMixer(mixerInfo);
                        DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, targetFormat);
                        
                        if (mixer.isLineSupported(lineInfo)) {
                            line = (TargetDataLine) mixer.getLine(lineInfo);
                            line.open(targetFormat, BUFFER_SIZE * 2);
                            System.out.println("Successfully opened microphone: " + mixerInfo.getName());
                            System.out.println("Format: " + line.getFormat());
                            break;
                        }
                    } catch (Exception e) {
                        // Try next mixer
                        continue;
                    }
                }
                
                if (line == null) {
                    System.out.println("ERROR: Could not open any microphone");
                    callback.accept("Archer: Could not access microphone. Please check permissions.", true);
                    isListening = false;
                    return;
                }
                
                targetLine = line;
                targetLine.start();
                System.out.println("Listening: true - Microphone active, waiting for speech...");
                
                Recognizer recognizer = new Recognizer(model, (int)SAMPLE_RATE);
                byte[] buffer = new byte[BUFFER_SIZE];
                
                long lastVoiceTime = System.currentTimeMillis();
                long firstVoiceTime = 0;
                boolean hasDetectedSpeech = false;
                StringBuilder finalText = new StringBuilder();
                int consecutiveSilentFrames = 0;
                int requiredSilentFrames = (int)(SILENCE_DURATION_MS / 50); // frames needed for silence
                
                while (isListening) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length);
                    if (bytesRead <= 0) {
                        Thread.sleep(10);
                        continue;
                    }
                    
                    double amplitude = calculateAmplitude(buffer, bytesRead);
                    
                    if (amplitude > SILENCE_THRESHOLD) {
                        consecutiveSilentFrames = 0;
                        
                        if (!hasDetectedSpeech) {
                            firstVoiceTime = System.currentTimeMillis();
                            hasDetectedSpeech = true;
                            System.out.println("Speech detected, starting transcription...");
                        }
                        lastVoiceTime = System.currentTimeMillis();
                        
                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            String resultJson = recognizer.getResult();
                            String txt = extractText(resultJson);
                            if (!txt.isBlank()) {
                                txt = cleanTranscription(txt);
                                finalText.append(txt).append(" ");
                                System.out.println("Recognized: " + txt);
                            }
                        } else {
                            String partialJson = recognizer.getPartialResult();
                            String partial = extractText(partialJson);
                            if (!partial.isBlank() && hasDetectedSpeech) {
                                partial = cleanTranscription(partial);
                                callback.accept(partial, false);
                            }
                        }
                    } else {
                        consecutiveSilentFrames++;
                    }
                    
                    // Only consider ending if we've had enough speech
                    long speechDuration = hasDetectedSpeech ? 
                        (System.currentTimeMillis() - firstVoiceTime) : 0;
                    
                    if (hasDetectedSpeech && 
                        speechDuration >= MIN_SPEECH_DURATION_MS &&
                        consecutiveSilentFrames >= requiredSilentFrames) {
                        
                        System.out.println("Silence detected after speech, finalizing...");
                        
                        // Get final result
                        String finalJson = recognizer.getResult();
                        String finalTxt = extractText(finalJson);
                        if (!finalTxt.isBlank()) {
                            finalTxt = cleanTranscription(finalTxt);
                            finalText.append(finalTxt);
                        }
                        
                        String completeText = finalText.toString().trim();
                        if (!completeText.isEmpty()) {
                            System.out.println("Final text: " + completeText);
                            callback.accept(completeText, true);
                        }
                        
                        // Signal session end
                        callback.accept("__LISTENING_SESSION_END__", true);
                        break;
                    }
                    
                    Thread.sleep(50);
                }
                
                recognizer.close();
                targetLine.stop();
                targetLine.close();
                System.out.println("Listening session ended");
                
            } catch (Exception e) {
                System.err.println("ERROR in voice listening: " + e.getMessage());
                e.printStackTrace();
                callback.accept("Archer: Error in voice listening: " + e.getMessage(), true);
            } finally {
                isListening = false;
            }
        });
        
        listeningThread.start();
    }

    private double calculateAmplitude(byte[] buffer, int bytesRead) {
        if (bytesRead <= 0) return 0.0;
        double sum = 0;
        int samples = bytesRead / 2;
        for (int i = 0; i < bytesRead - 1; i += 2) {
            int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
            sum += sample * sample;
        }
        return Math.sqrt(sum / samples) / 32768.0;
    }

    private String extractText(String voskJson) {
        if (voskJson == null || voskJson.isBlank()) return "";
        try {
            JsonElement el = JsonParser.parseString(voskJson);
            if (el.isJsonObject()) {
                JsonObject obj = el.getAsJsonObject();
                if (obj.has("text")) {
                    return obj.get("text").getAsString();
                }
            }
        } catch (Exception e) {
            // Fallback parsing
            int idx = voskJson.indexOf("\"text\"");
            if (idx >= 0) {
                int colon = voskJson.indexOf(':', idx);
                if (colon >= 0) {
                    int start = voskJson.indexOf('"', colon);
                    int end = voskJson.indexOf('"', start + 1);
                    if (start >= 0 && end > start) {
                        return voskJson.substring(start + 1, end);
                    }
                }
            }
        }
        return "";
    }

    private String cleanTranscription(String text) {
        if (text == null || text.isBlank()) return text;
        
        text = text.trim().replaceAll("\\s+", " ");
        
        // Fix common transcription errors
        text = text.replaceAll("(?i)\\b(trace|trays|trees|traces|tray|trey)\\b", "trace");
        text = text.replaceAll("(?i)\\bcheck battery life\\b", "check battery life");
        text = text.replaceAll("(?i)\\bcheck battery\\b", "check battery");
        text = text.replaceAll("(?i)\\b(battery|batteries|batter)\\b", "battery");
        text = text.replaceAll("(?i)\\b(open|opened|opening)\\b", "open");
        text = text.replaceAll("(?i)\\b(chrome|chrome browser|google chrome)\\b", "chrome");
        text = text.replaceAll("(?i)\\b(um|uh|ah|er|hmm|like|you know)\\b", "");
        text = text.replaceAll("\\s+", " ").trim();
        
        return text;
    }

    public synchronized void stopListening() {
        isListening = false;
        try {
            if (listeningThread != null && listeningThread.isAlive()) {
                listeningThread.interrupt();
                listeningThread.join(1000);
            }
            if (targetLine != null && targetLine.isOpen()) {
                targetLine.stop();
                targetLine.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public boolean isListening() {
        return isListening;
    }
}