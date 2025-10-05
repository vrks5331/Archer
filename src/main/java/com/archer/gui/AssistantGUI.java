package com.archer.gui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
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

        // Microphone button
        Button micButton = new Button("🎤");
        micButton.setShape(new Circle(25));
        micButton.setMinSize(50, 50);
        micButton.setMaxSize(50, 50);
        micButton.setStyle("-fx-background-color: crimson; -fx-text-fill: white; -fx-font-size: 20px;");
        micButton.setOnAction(e -> transcribeSpeech()); // for now just test message

        // Layout
        VBox layout = new VBox(20, title, scrollPane, micButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(20));

        Scene scene = new Scene(layout, 400, 600);
        stage.setScene(scene);
        stage.setTitle("Archer");
        stage.show();

        // initialize NLP processor and voice input once GUI is loaded
        CommandProcessor cp = new CommandProcessor();
        voiceInput = new VoiceInput(cp);
    }

    private boolean listening = false;

private void transcribeSpeech() {
    if (listening) return;
    listening = true;

    Label status = new Label("Say 'Hey Archer' to start...");
    chatBox.getChildren().add(status);

    voiceInput.start(text -> {
        Platform.runLater(() -> {
            if (text.startsWith("Archer:") || text.equals("(stopped listening)")) {
                Label botLabel = new Label(text);
                botLabel.setStyle("-fx-background-color: #ffe0e0; -fx-padding: 8; -fx-background-radius: 8;");
                chatBox.getChildren().add(botLabel);
            } else {
                // user text
                if (currentUserLabel == null || text.equals("(stopped listening)")) {
                    currentUserLabel = new Label("You: " + text);
                    currentUserLabel.setStyle("-fx-background-color: lightgray; -fx-padding: 8; -fx-background-radius: 8;");
                    chatBox.getChildren().add(currentUserLabel);
                } else {
                    // update same bubble for partial text
                    currentUserLabel.setText("You: " + text);
                }

                // reset label when final speech ends
                if (text.equals("(stopped listening)")) {
                    currentUserLabel = null;
                }
            }
        });
    });
}



    public static void main(String[] args) {
        launch();
    }
}
