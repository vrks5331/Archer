package com.archer.voice;

import java.util.function.Consumer;
import com.archer.nlp.CommandProcessor;

public class VoiceInput {
    private CommandProcessor commandProcessor;
    private VoiceTranscription transcription;
    private volatile boolean isActive = false;
    private Consumer<String> guiCallback;
    private volatile boolean isProcessingCommand = false;
    private volatile boolean shouldRestart = false;

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
        System.out.println("VoiceInput.start() called");
        this.guiCallback = guiCallback;
        
        if (guiCallback == null) {
            System.err.println("ERROR: guiCallback is null!");
            return;
        }
        
        System.out.println("VoiceInput: Starting initial listening session...");
        startListeningSession();
    }

    private synchronized void startListeningSession() {
        // Prevent multiple simultaneous sessions
        if (transcription.isListening()) {
            System.out.println("VoiceInput: Already listening, skipping start");
            return;
        }
        
        System.out.println("VoiceInput: Starting new listening session (active: " + isActive + ")");
        shouldRestart = false;
        
        transcription.startListening((text, isFinal) -> {
            if (text == null) {
                System.out.println("VoiceInput: Received null text");
                return;
            }
            
            System.out.println("VoiceInput: Received text: '" + text + "', isFinal: " + isFinal);
            
            // Handle session end signal
            if (text.equals("__LISTENING_SESSION_END__")) {
                System.out.println("VoiceInput: Session ended, active=" + isActive + 
                                 ", processing=" + isProcessingCommand);
                
                // Restart listening if not processing and should continue
                if (!isProcessingCommand && shouldRestart) {
                    System.out.println("VoiceInput: Restarting listening session...");
                    scheduleRestart();
                } else if (!isProcessingCommand) {
                    System.out.println("VoiceInput: Session ended normally, restarting...");
                    scheduleRestart();
                }
                return;
            }
            
            // Pass through system messages
            if (text.startsWith("Archer:")) {
                guiCallback.accept(text);
                return;
            }
            
            if (isFinal) {
                String cleaned = text.trim().toLowerCase();
                
                // Ignore very short/empty text
                if (cleaned.isEmpty() || cleaned.length() < 2) {
                    System.out.println("VoiceInput: Ignoring short text, restarting...");
                    shouldRestart = true;
                    return;
                }
                
                // Check for activation command
                boolean isTraceOn = cleaned.contains("trace") && 
                    (cleaned.contains("on") || cleaned.contains("activate") ||
                     cleaned.equals("trace on") || cleaned.equals("traceon"));
                
                // Check for deactivation command
                boolean isTraceOff = cleaned.contains("trace") && 
                    (cleaned.contains("off") || cleaned.contains("deactivate") || 
                     cleaned.contains("stop") || cleaned.equals("trace off") || 
                     cleaned.equals("traceoff"));
                
                if (isTraceOn) {
                    System.out.println("VoiceInput: Detected 'trace on' - activating");
                    isActive = true;
                    isProcessingCommand = false;
                    shouldRestart = true;
                    guiCallback.accept("Archer: Voice activation enabled. Listening for commands...");
                    scheduleRestart();
                    return;
                }
                
                if (isTraceOff) {
                    System.out.println("VoiceInput: Detected 'trace off' - deactivating");
                    isActive = false;
                    isProcessingCommand = false;
                    shouldRestart = false;
                    guiCallback.accept("Archer: Voice activation disabled.");
                    stop();
                    return;
                }
                
                // If not active, ignore commands but keep listening for wake word
                if (!isActive) {
                    System.out.println("VoiceInput: Not active, ignoring command: " + cleaned);
                    shouldRestart = true;
                    return;
                }
                
                // Process the command
                System.out.println("VoiceInput: Processing command: " + text.trim());
                isProcessingCommand = true;
                shouldRestart = false;
                guiCallback.accept("You: " + text.trim());
                
                // Process command with callback
                commandProcessor.processCommand(text.trim(), response -> {
                    guiCallback.accept(response);
                    
                    // After command completes, restart listening
                    new Thread(() -> {
                        try {
                            Thread.sleep(2000); // Wait 2 seconds for response to complete
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        if (isActive) {
                            System.out.println("VoiceInput: Command complete, restarting listening...");
                            isProcessingCommand = false;
                            shouldRestart = true;
                            startListeningSession();
                        } else {
                            isProcessingCommand = false;
                        }
                    }).start();
                });
            } else {
                // Partial text - only show if active
                if (isActive && !text.trim().isEmpty()) {
                    guiCallback.accept("You (partial): " + text.trim());
                }
            }
        });
    }

    private void scheduleRestart() {
        new Thread(() -> {
            try {
                Thread.sleep(500); // Wait 500ms before restarting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            startListeningSession();
        }).start();
    }

    public void stop() {
        isActive = false;
        isProcessingCommand = false;
        shouldRestart = false;
        if (transcription != null) {
            transcription.stopListening();
        }
    }

    public boolean isActive() {
        return isActive;
    }
}