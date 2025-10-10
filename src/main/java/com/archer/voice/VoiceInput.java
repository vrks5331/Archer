package com.archer.voice;

import java.util.function.Consumer;
import com.archer.nlp.CommandProcessor;

public class VoiceInput {
    private CommandProcessor commandProcessor;
    private VoiceTranscription transcription;

    public VoiceInput(CommandProcessor cp) {
        this.commandProcessor = cp;
        try {
            this.transcription = new VoiceTranscription();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Failed to initialize voice transcription: " + e.getMessage());
        }
    }

    public void start(Consumer<String> guiCallback) {
        transcription.startListening((text, isFinal) -> {
            if (isFinal) {
                String cleaned = text.trim();
                if (cleaned.isEmpty() || cleaned.length() < 2) {
                    return; // ignore noise
                }
                guiCallback.accept("You: " + cleaned);
                commandProcessor.processCommand(cleaned, guiCallback);
            } else {
                if (text != null && !text.trim().isEmpty()) {
                    // partial text, send as is
                    guiCallback.accept("You (partial): " + text.trim());
                }
            }
        });
    }

    public void stop() {
        if (transcription != null) {
            transcription.stopListening();
        }
    }

}
