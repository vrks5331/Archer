package com.archer.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.stage.Stage;

import com.archer.nlp.CommandProcessor;
import com.archer.voice.VoiceInput;

public class AssistantGUI extends Application {

    private VBox chatBox;
    private VoiceInput voiceInput;
    private CommandProcessor commandProcessor;
    private Label currentUserLabel;
    private Label currentArcherLabel; // For streaming responses
    private Button voiceToggleButton;
    private TextField textInput;
    private Label statusLabel;

    @Override
    public void start(Stage stage) {
        // Title
        Label title = new Label("Archer");
        title.setFont(new Font("Segoe UI", 24));
        title.setTextFill(Color.DARKRED);

        // Chat area
        chatBox = new VBox(10);
        chatBox.setPadding(new Insets(10));
        chatBox.setPrefHeight(400);

        ScrollPane scrollPane = new ScrollPane(chatBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        // Status label to show voice activation status
        statusLabel = new Label("Voice disabled - Click button to enable");
        statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");

        // Circular voice toggle button
        voiceToggleButton = new Button("🎤");
        voiceToggleButton.setStyle(
            "-fx-background-color: #cccccc; " +
            "-fx-background-radius: 50; " +
            "-fx-min-width: 50; " +
            "-fx-min-height: 50; " +
            "-fx-max-width: 50; " +
            "-fx-max-height: 50; " +
            "-fx-font-size: 20px; " +
            "-fx-cursor: hand;"
        );
        voiceToggleButton.setOnAction(e -> toggleVoice());

        // Text input field (rounded and long)
        textInput = new TextField();
        textInput.setPromptText("Type your message here...");
        textInput.setStyle(
            "-fx-background-radius: 20; " +
            "-fx-border-radius: 20; " +
            "-fx-padding: 10 15; " +
            "-fx-font-size: 14px;"
        );
        textInput.setOnAction(e -> sendTextMessage());

        // Status label in its own area
        HBox statusBox = new HBox(10, statusLabel);
        statusBox.setAlignment(Pos.CENTER_LEFT);
        statusBox.setPadding(new Insets(10));

        // Bottom input area with button next to textbox
        HBox inputBox = new HBox(10, textInput, voiceToggleButton);
        inputBox.setAlignment(Pos.CENTER);
        inputBox.setPadding(new Insets(10));
        HBox.setHgrow(textInput, Priority.ALWAYS);

        // Main layout
        VBox layout = new VBox(10, title, scrollPane, statusBox, inputBox);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        Scene scene = new Scene(layout, 500, 700);
        stage.setScene(scene);
        stage.setTitle("Archer");
        stage.show();

        // initialize NLP processor and voice input once GUI is loaded
        System.out.println("GUI: Initializing CommandProcessor...");
        commandProcessor = new CommandProcessor();
        System.out.println("GUI: Initializing VoiceInput...");
        voiceInput = new VoiceInput(commandProcessor);
        System.out.println("GUI: Starting voice input...");
        
        // Add initial status message
        Label initLabel = new Label("Archer: Ready. Click the microphone button to start voice chat.");
        initLabel.setStyle("-fx-background-color: #ffe0e0; -fx-padding: 8; -fx-background-radius: 8;");
        chatBox.getChildren().add(initLabel);
        
        // Start voice input (but not active initially)
        voiceInput.start(text -> {
            Platform.runLater(() -> {
                if (text == null) return;

                // Update status label and button based on activation state
                updateVoiceButtonState();

                // Assistant/system messages (already formatted)
                if (text.startsWith("Archer:") || text.equals("(stopped listening)")) {
                    // For streaming responses, update existing label instead of creating new ones
                    if (currentArcherLabel != null && chatBox.getChildren().contains(currentArcherLabel)) {
                        currentArcherLabel.setText(text);
                    } else {
                        currentArcherLabel = new Label(text);
                        currentArcherLabel.setStyle("-fx-background-color: #ffe0e0; -fx-padding: 8; -fx-background-radius: 8;");
                        chatBox.getChildren().add(currentArcherLabel);
                    }
                    return;
                }
                
                // Handle "Listening:" prefix (partial transcription before activation)
                if (text.startsWith("Listening: ")) {
                    if (currentUserLabel == null) {
                        currentUserLabel = new Label(text);
                        currentUserLabel.setStyle("-fx-background-color: #e0e0e0; -fx-padding: 8; -fx-background-radius: 8;");
                        chatBox.getChildren().add(currentUserLabel);
                    } else {
                        currentUserLabel.setText(text);
                    }
                    return;
                }

                // User messages come prefixed by VoiceInput: "You (partial): ..." or "You: ..."
                if (text.startsWith("You (partial): ")) {
                    if (currentUserLabel == null) {
                        currentUserLabel = new Label(text);
                        currentUserLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 8; -fx-background-radius: 8;");
                        chatBox.getChildren().add(currentUserLabel);
                    } else {
                        currentUserLabel.setText(text);
                    }
                } else if (text.startsWith("You: ")) {
                    // final user message — display as a final bubble and clear currentUserLabel
                    // Remove partial label if it exists
                    if (currentUserLabel != null && chatBox.getChildren().contains(currentUserLabel)) {
                        chatBox.getChildren().remove(currentUserLabel);
                    }
                    Label userLabel = new Label(text);
                    userLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 8; -fx-background-radius: 8;");
                    chatBox.getChildren().add(userLabel);
                    currentUserLabel = null;
                    currentArcherLabel = null; // Reset Archer label for next response
                } else {
                    // fallback: treat as final user text
                    Label userLabel = new Label("You: " + text);
                    userLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 8; -fx-background-radius: 8;");
                    chatBox.getChildren().add(userLabel);
                    currentUserLabel = null;
                }
            });
        });
    }

    private void toggleVoice() {
        if (voiceInput.isActive()) {
            // Turn off
            voiceInput.stop();
            updateVoiceButtonState();
        } else {
            // Turn on
            voiceInput.activate();
            updateVoiceButtonState();
        }
    }

    private void updateVoiceButtonState() {
        if (voiceInput.isActive()) {
            statusLabel.setText("Voice activated - Listening for commands...");
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
            voiceToggleButton.setStyle(
                "-fx-background-color: #ff4444; " +
                "-fx-background-radius: 50; " +
                "-fx-min-width: 50; " +
                "-fx-min-height: 50; " +
                "-fx-max-width: 50; " +
                "-fx-max-height: 50; " +
                "-fx-font-size: 20px; " +
                "-fx-cursor: hand;"
            );
        } else {
            statusLabel.setText("Voice disabled - Click button to enable");
            statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");
            voiceToggleButton.setStyle(
                "-fx-background-color: #cccccc; " +
                "-fx-background-radius: 50; " +
                "-fx-min-width: 50; " +
                "-fx-min-height: 50; " +
                "-fx-max-width: 50; " +
                "-fx-max-height: 50; " +
                "-fx-font-size: 20px; " +
                "-fx-cursor: hand;"
            );
        }
    }

    private void sendTextMessage() {
        String text = textInput.getText().trim();
        if (text.isEmpty()) {
            return;
        }

        // Clear input
        textInput.clear();

        // Display user message
        Label userLabel = new Label("You: " + text);
        userLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 8; -fx-background-radius: 8;");
        chatBox.getChildren().add(userLabel);
        currentUserLabel = null;
        currentArcherLabel = null; // Reset for new response

        // Process command with Gemini API
        commandProcessor.processCommand(text, response -> {
            Platform.runLater(() -> {
                if (response != null && !response.trim().isEmpty()) {
                    // Update or create Archer label
                    if (currentArcherLabel != null && chatBox.getChildren().contains(currentArcherLabel)) {
                        currentArcherLabel.setText(response);
                    } else {
                        currentArcherLabel = new Label(response);
                        currentArcherLabel.setStyle("-fx-background-color: #ffe0e0; -fx-padding: 8; -fx-background-radius: 8;");
                        chatBox.getChildren().add(currentArcherLabel);
                    }
                }
            });
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
