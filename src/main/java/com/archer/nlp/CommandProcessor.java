package com.archer.nlp;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.archer.actions.ActionExecutor;

public class CommandProcessor {

    private final Client geminiClient;
    private final ExecutorService executor;
    private JsonParser jp = new JsonParser();

    public CommandProcessor() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("GEMINI_API_KEY environment variable is not set!");
        }

        this.geminiClient = Client.builder().apiKey(apiKey).build();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void processCommand(String command, Consumer<String> guiCallback) {
        executor.submit(() -> {
            try {
                // === 1. Classify the command ===
                String classificationPrompt = String.format(
                        "Classify the following user command as either 'LOCAL' (for system/PC control) or 'AI'. "
                                + "Reply ONLY with 'LOCAL' or 'AI'. Command: '%s'",
                        command);

                GenerateContentResponse classificationResponse = geminiClient.models.generateContent(
                        "gemini-2.5-flash",
                        classificationPrompt,
                        null);

                String classification = classificationResponse.text().trim().toUpperCase();

                // === 2. Handle local system commands ===
                if (classification.equals("LOCAL")) {

                    String jsonPrompt = String.format("""
                            You are an operating system command interpreter. The user will give a natural
                            language command describing what to do on their computer.
                            Convert it into structured JSON specifying the intended local action.

                            Examples:
                            "Open Chrome" → {"action":"open_app","target":"chrome"}
                            "Increase volume by 10%%" → {"action":"adjust_volume","value":"10","direction":"up"}
                            "Turn off Bluetooth" → {"action":"toggle_bluetooth","value":"off"}
                            "Check Wi-Fi connection" → {"action":"check_wifi"}
                            "Play next song on Spotify" → {"action":"media_control","target":"next_track"}

                            Respond with only JSON.
                            User command: %s
                            """, command);

                    GenerateContentResponse jsonResponse = geminiClient.models.generateContent(
                            "gemini-2.5-flash",
                            jsonPrompt,
                            null);

                    String json = jsonResponse.text().trim();
                    JsonObject obj = jp.parseString(json).getAsJsonObject();

                    String action = obj.has("action") ? obj.get("action").getAsString() : null;
                    String target = obj.has("target") ? obj.get("target").getAsString() : null;
                    String value = obj.has("value") ? obj.get("value").getAsString() : null;

                    // === 3. Execute local action ===
                    String result = ActionExecutor.execute(action, target, value);

                    // === 4. Generate confirmation ===
                    String confirmationPrompt = String.format(
                            "As Archer, confirm you executed the action: '%s'. Be concise and professional.",
                            result);

                    streamGeminiResponse("gemini-2.5-flash", confirmationPrompt, guiCallback);

                } else {
                    // === 5. Handle AI/general commands ===
                    streamGeminiResponse("gemini-2.5-flash", command, guiCallback);
                }

            } catch (Exception e) {
                e.printStackTrace();
                guiCallback.accept("Archer: Sorry, something went wrong.");
            }
        });
    }

    private void streamGeminiResponse(String model, String prompt, Consumer<String> guiCallback) {
        executor.submit(() -> {
            geminiClient.models.generateContentStream(model, prompt, null)
                    .forEach(responsePart -> {
                        String textChunk = responsePart.text();
                        if (textChunk != null && !textChunk.isEmpty()) {
                            guiCallback.accept("Archer: " + textChunk);
                        }
                    });
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
