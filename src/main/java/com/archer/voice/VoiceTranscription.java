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
    private static final double SILENCE_THRESHOLD = 0.02;
    private static final int SILENCE_DURATION_MS = 5000;

    private TargetDataLine targetLine;
    private Model model;

    // Constructor: load Vosk model
    public VoiceTranscription() throws Exception {
        // Allow overriding the model path via system property or environment variable.
        String modelPath = System.getProperty("vosk.model.path");
        if (modelPath == null || modelPath.isBlank()) {
            modelPath = System.getenv("VOSK_MODEL_PATH");
        }
        if (modelPath == null || modelPath.isBlank()) {
            // default path kept for backward compatibility
            modelPath = "C:\\Users\\srive\\vosk-model-small-en-us-0.15\\vosk-model-small-en-us-0.15";
        }
        System.out.println("Using Vosk model path: " + modelPath);
        model = new Model(modelPath);
    }

    /**
     * Start listening to microphone and feed recognized text to callback
     * @param callback: (text, isFinal) where isFinal=true if speech chunk is complete
     */
    public void startListening(BiConsumer<String, Boolean> callback) {
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("Mic not supported");
                    return;
                }

                targetLine = (TargetDataLine) AudioSystem.getLine(info);
                targetLine.open(format);
                targetLine.start();

                Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);

                byte[] buffer = new byte[BUFFER_SIZE];
                long lastVoiceTime = System.currentTimeMillis();
                StringBuilder finalText = new StringBuilder();

                while (true) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length);
                    double amplitude = calculateAmplitude(buffer, bytesRead);

                    if (amplitude > SILENCE_THRESHOLD) {
                        lastVoiceTime = System.currentTimeMillis();

                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            String resultJson = recognizer.getResult();
                            System.out.println("Raw recognizer output: " + resultJson);
                            String txt = extractText(resultJson);
                            if (!txt.isBlank()) {
                                finalText.append(txt).append(" ");
                                callback.accept(finalText.toString().trim(), true);
                            }
                        } else {
                            String partialJson = recognizer.getPartialResult();
                            String partial = extractText(partialJson);
                            if (!partial.isBlank()) {
                                callback.accept(partial, false);
                            }
                        }
                    }

                    if (System.currentTimeMillis() - lastVoiceTime > SILENCE_DURATION_MS) {
                        // final result before shutdown
                        String finalJson = recognizer.getResult();
                        String finalTxt = extractText(finalJson);
                        if (!finalTxt.isBlank()) {
                            callback.accept(finalTxt, true);
                        }
                        System.out.println("Archer: (offline due to silence)");
                        callback.accept("Archer: (offline due to silence)", true);
                        try {
                            targetLine.stop();
                            targetLine.close();
                        } catch (Exception ignore) {
                        }
                        recognizer.close();
                        break;
                    }

                    Thread.sleep(50);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private double calculateAmplitude(byte[] buffer, int bytesRead) {
        if (bytesRead <= 0) return 0.0;
        double sum = 0;
        for (int i = 0; i < bytesRead - 1; i += 2) {
            int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
            sum += sample * sample;
        }
        return Math.sqrt(sum / Math.max(1, (bytesRead / 2))) / 32768.0;
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
            // fallback: attempt to locate "text" key manually
            int idx = voskJson.indexOf("\"text\"");
            if (idx >= 0) {
                int colon = voskJson.indexOf(':', idx);
                if (colon >= 0) {
                    int start = voskJson.indexOf('"', colon);
                    int end = voskJson.indexOf('"', start + 1);
                    if (start >= 0 && end > start) return voskJson.substring(start + 1, end);
                }
            }
            // final fallback: regex-like manual extraction to tolerate spaces/newlines
            try {
                String s = voskJson.replaceAll("\\r\\n", " ").replaceAll("\\n", " ");
                int t = s.indexOf("\"text\"");
                if (t >= 0) {
                    int colon = s.indexOf(':', t);
                    int firstQuote = s.indexOf('"', colon);
                    int secondQuote = s.indexOf('"', firstQuote + 1);
                    if (firstQuote >= 0 && secondQuote > firstQuote) return s.substring(firstQuote + 1, secondQuote);
                }
            } catch (Exception ignore) {
            }
        }
        return "";
    }

    public void stopListening() {
        try {
            if (targetLine != null && targetLine.isOpen()) {
                targetLine.stop();
                targetLine.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
