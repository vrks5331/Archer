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
    private Label currentUserLabel;

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
        Label statusLabel = new Label("Say 'Trace, on' to activate voice commands");
        statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");

        // Layout
        VBox layout = new VBox(20, title, scrollPane, statusLabel);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Scene scene = new Scene(layout, 400, 600);
        stage.setScene(scene);
        stage.setTitle("Archer");
        stage.show();

        // initialize NLP processor and voice input once GUI is loaded
        System.out.println("GUI: Initializing CommandProcessor...");
        CommandProcessor cp = new CommandProcessor();
        System.out.println("GUI: Initializing VoiceInput...");
        voiceInput = new VoiceInput(cp);
        System.out.println("GUI: Starting voice input...");
        
        // Add initial status message
        Label initLabel = new Label("Archer: Initializing voice input...");
        initLabel.setStyle("-fx-background-color: #ffe0e0; -fx-padding: 8; -fx-background-radius: 8;");
        chatBox.getChildren().add(initLabel);
        
        // Start voice input automatically (will wait for "trace, on")
        voiceInput.start(text -> {
            Platform.runLater(() -> {
                if (text == null) return;

                // Update status label based on activation state
                if (voiceInput.isActive()) {
                    statusLabel.setText("Voice activated - Listening for commands...");
                    statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
                } else {
                    statusLabel.setText("Say 'Trace, on' to activate voice commands");
                    statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");
                }

                // Assistant/system messages (already formatted)
                if (text.startsWith("Archer:") || text.equals("(stopped listening)")) {
                    Label botLabel = new Label(text);
                    botLabel.setStyle("-fx-background-color: #ffe0e0; -fx-padding: 8; -fx-background-radius: 8;");
                    chatBox.getChildren().add(botLabel);
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
                    Label userLabel = new Label(text);
                    userLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 8; -fx-background-radius: 8;");
                    chatBox.getChildren().add(userLabel);
                    currentUserLabel = null;
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

    public static void main(String[] args) {
        launch();
    }
}
