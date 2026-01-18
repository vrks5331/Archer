package com.archer.voice;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import com.archer.nlp.CommandProcessor;

public class VoiceInput {
    private CommandProcessor commandProcessor;
    private VoiceTranscription transcription;
    private volatile boolean isActive = false;
    private Consumer<String> guiCallback;
    private volatile boolean isProcessingCommand = false;
    private volatile boolean waitingForCommandResponse = false;
    private final ExecutorService executorService;

    public VoiceInput(CommandProcessor cp) {
        this.commandProcessor = cp;
        this.executorService = Executors.newCachedThreadPool();
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
        
        transcription.startListening((text, isFinal) -> {
            if (text == null) {
                System.out.println("VoiceInput: Received null text");
                return;
            }
            
            System.out.println("VoiceInput: Received text: '" + text + "', isFinal: " + isFinal);
            
            // Ignore all speech while waiting for command response (but allow final commands through)
            if ((waitingForCommandResponse || isProcessingCommand) && !isFinal) {
                System.out.println("VoiceInput: Ignoring speech while waiting for response: " + text);
                return;
            }
            
            // Handle command finalized signal (session continues, just command is done)
            // This comes AFTER the final text, so we should have already processed the command
            if (text.equals("__COMMAND_FINALIZED__")) {
                System.out.println("VoiceInput: Command finalized signal received (command should already be processing)");
                // This is just a signal, the actual command was already sent with isFinal=true
                // Don't process this as a command, just continue
                return;
            }
            
            // Pass through system messages
            if (text.startsWith("Archer:")) {
                guiCallback.accept(text);
                return;
            }
            
            if (isFinal) {
                System.out.println("VoiceInput: Received FINAL text: '" + text + "'");
                String cleaned = text.trim().toLowerCase();
                
                // Ignore very short/empty text
                if (cleaned.isEmpty() || cleaned.length() < 2) {
                    System.out.println("VoiceInput: Ignoring short text, continuing to listen");
                    waitingForCommandResponse = false; // Not waiting, just continue listening
                    return;
                }
                
                System.out.println("VoiceInput: Final text is valid, processing...");
                
                // If not active, ignore ALL commands (including "off") but keep listening
                if (!isActive) {
                    System.out.println("VoiceInput: Not active, ignoring command: " + cleaned);
                    return;
                }
                
                // Check for deactivation command - "off" or "deactivate" (can be part of phrase)
                // Only check if voice is active
                boolean isOff = cleaned.equals("off") || cleaned.equals("deactivate") || 
                               cleaned.equals("turn off") || cleaned.equals("disable") ||
                               cleaned.equals("stop") ||
                               cleaned.contains(" off ") || cleaned.endsWith(" off") ||
                               cleaned.startsWith("off ") || cleaned.contains("deactivate");
                
                System.out.println("VoiceInput: Checking deactivation - isOff: " + isOff + ", isActive: " + isActive);
                
                if (isOff) {
                    System.out.println("VoiceInput: Detected 'off' - deactivating");
                    isActive = false;
                    isProcessingCommand = false;
                    waitingForCommandResponse = false;
                    guiCallback.accept("Archer: Voice activation disabled.");
                    // Don't stop the session, just deactivate - user can reactivate with button
                    return;
                }
                
                // Process the command
                String commandText = text.trim();
                System.out.println("VoiceInput: Processing command: '" + commandText + "'");
                
                if (commandProcessor == null) {
                    System.err.println("VoiceInput: ERROR - commandProcessor is null!");
                    return;
                }
                
                if (guiCallback == null) {
                    System.err.println("VoiceInput: ERROR - guiCallback is null!");
                    return;
                }
                
                isProcessingCommand = true;
                waitingForCommandResponse = true;
                
                System.out.println("VoiceInput: Sending 'You: " + commandText + "' to GUI");
                guiCallback.accept("You: " + commandText);
                
                // Process command with callback
                System.out.println("VoiceInput: Calling commandProcessor.processCommand with: '" + commandText + "'");
                try {
                    commandProcessor.processCommand(commandText, response -> {
                        System.out.println("VoiceInput: Received response from commandProcessor: " + response);
                        if (response != null && !response.trim().isEmpty()) {
                            guiCallback.accept(response);
                            System.out.println("VoiceInput: Response sent to GUI callback");
                        } else {
                            System.out.println("VoiceInput: WARNING - Response was null or empty!");
                        }
                        
                        // After command completes, reset flags so we can accept new commands
                        executorService.submit(() -> {
                            try {
                                Thread.sleep(100); // Reduced delay for faster response
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            
                            synchronized (this) {
                                isProcessingCommand = false;
                                waitingForCommandResponse = false;
                                
                                System.out.println("VoiceInput: Command response complete, ready for next command");
                                // Session is still active, no need to restart - just ready for next command
                            }
                        });
                    });
                } catch (Exception e) {
                    System.err.println("VoiceInput: ERROR calling processCommand: " + e.getMessage());
                    e.printStackTrace();
                    isProcessingCommand = false;
                    waitingForCommandResponse = false;
                    guiCallback.accept("Archer: Error processing command: " + e.getMessage());
                }
            } else {
                // Partial text - show even before activation so user can see transcription
                if (!text.trim().isEmpty()) {
                    // Show partial results to help user see what's being transcribed
                    // This is especially helpful before activation
                    if (isActive) {
                        guiCallback.accept("You (partial): " + text.trim());
                    } else {
                        // Show partial results before activation (helps user see transcription)
                        guiCallback.accept("Listening: " + text.trim());
                    }
                }
            }
        });
    }


    public void activate() {
        System.out.println("VoiceInput: activate() called");
        isActive = true;
        isProcessingCommand = false;
        waitingForCommandResponse = false;
        // If session was stopped, restart it
        if (!transcription.isListening()) {
            System.out.println("VoiceInput: Starting listening session for activation");
            startListeningSession();
        }
        if (guiCallback != null) {
            guiCallback.accept("Archer: Voice activation enabled. Listening for commands...");
        }
    }

    public void stop() {
        System.out.println("VoiceInput: stop() called");
        isActive = false;
        isProcessingCommand = false;
        waitingForCommandResponse = false;
        if (transcription != null) {
            transcription.stopListening();
        }
        if (guiCallback != null) {
            guiCallback.accept("Archer: Voice activation disabled.");
        }
    }

    public void shutdown() {
        stop();
        if (executorService != null) {
            executorService.shutdown();
        }
    }

    public boolean isActive() {
        return isActive;
    }
}