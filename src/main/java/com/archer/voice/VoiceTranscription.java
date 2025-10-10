package com.archer.voice;

import javax.sound.sampled.*;
import java.util.function.BiConsumer;

import org.vosk.Model;
import org.vosk.Recognizer;

public class VoiceTranscription {

    private static final float SAMPLE_RATE = 16000; 
    private static final int BUFFER_SIZE = 1024;
    private static final double SILENCE_THRESHOLD = 0.02; 
    private static final int SILENCE_DURATION_MS = 5000;

    private TargetDataLine targetLine;
    private Model model;

    // Constructor: load Vosk model
    public VoiceTranscription() throws Exception {
        model = new Model("C:\\Users\\srive\\vosk-model-small-en-us-0.15\\vosk-model-small-en-us-0.15");
    }

    /**
     * Start listening to microphone and feed recognized text to callback
     * @param callback: (text, isFinal) where isFinal=true if speech chunk is complete
     */
    public void startListening(BiConsumer<String, Boolean> callback) {
        new Thread(() -> {
            try {
                Recognizer recognizer = new Recognizer(model, SAMPLE_RATE);
                AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

                if (!AudioSystem.isLineSupported(info)) {
                    System.out.println("Mic not supported");
                    return;
                }

                targetLine = (TargetDataLine) AudioSystem.getLine(info);
                targetLine.open(format);
                targetLine.start();

                byte[] buffer = new byte[BUFFER_SIZE];
                long lastVoiceTime = System.currentTimeMillis();
                StringBuilder finalText = new StringBuilder();

                while (true) {
                    int bytesRead = targetLine.read(buffer, 0, buffer.length);
                    double amplitude = calculateAmplitude(buffer, bytesRead);

                    if (amplitude > SILENCE_THRESHOLD) {
                        lastVoiceTime = System.currentTimeMillis();
                        String textChunk = "";

                        if (recognizer.acceptWaveForm(buffer, bytesRead)) {
                            textChunk = recognizer.getResult();
                            System.out.println("Raw recognizer output: " + textChunk);
                            
                            // final result
                            finalText.append(extractText(textChunk)).append(" ");
                            callback.accept("You: " + finalText.toString().trim(), true);
                        } else {
                            textChunk = recognizer.getPartialResult(); // partial
                            callback.accept("You: " + extractText(textChunk), false);
                        }
                    }

                    if (System.currentTimeMillis() - lastVoiceTime > SILENCE_DURATION_MS) {
                        callback.accept(recognizer.getResult(), true);
                        System.out.println("Archer: (offline due to silence)");
                        targetLine.stop();
                        targetLine.close();
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
        double sum = 0;
        for (int i = 0; i < bytesRead; i += 2) {
            int sample = (buffer[i + 1] << 8) | (buffer[i] & 0xFF);
            sum += sample * sample;
        }
        return Math.sqrt(sum / (bytesRead / 2)) / 32768.0;
    }

    private String extractText(String voskJson) {
        // Vosk returns JSON like {"text": "hello world"}
        int idx = voskJson.indexOf("\"text\" :");
        if (idx >= 0) {
            int start = voskJson.indexOf("\"", idx + 7) + 1;
            int end = voskJson.indexOf("\"", start);
            if (start >= 0 && end > start) return voskJson.substring(start, end);
            
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
