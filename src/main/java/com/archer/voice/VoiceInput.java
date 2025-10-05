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
        }
    }

    public void start(Consumer<String> guiCallback) {
    transcription.startListening((text, isFinal) -> {
        if (isFinal) {
            // prepend "You: " for final bubble
            String finalText = "You: " + text;
            guiCallback.accept(finalText);

            String command = text.trim();
            if (!command.isEmpty()) {
                commandProcessor.processCommand(command, guiCallback);
            }
        } else {
            // partial text, send as is
            guiCallback.accept(text);
        }
    });
}

}
