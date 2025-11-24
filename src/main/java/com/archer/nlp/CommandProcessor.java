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

    public CommandProcessor() {
        String apiKey = System.getenv("GEMINI_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("GEMINI_API_KEY environment variable is not set!");
        }

        this.geminiClient = Client.builder().apiKey(apiKey).build();
        this.executor = Executors.newSingleThreadExecutor();
    }

    public void processCommand(String command, Consumer<String> guiCallback) {
        System.out.println("Processing command: " + command);
        executor.submit(() -> {
            try {
                // Combined prompt: classify and generate JSON in one call for speed
                String combinedPrompt = String.format("""
                        Analyze the following user command and respond with JSON.
                        
                        Step 1: Determine if this is a LOCAL system command or AI command.
                        - LOCAL = any request to control the computer, open/close apps, adjust system settings, 
                          check system information, toggle hardware, manage files, run commands, etc.
                        - AI = general questions, conversations, explanations, creative tasks
                        
                        Step 2: If LOCAL, convert to action JSON. If AI, respond with {"type":"AI","query":"%s"}
                        
                        Available LOCAL actions:
                        - open_app: {"action":"open_app","target":"app_name"}
                        - search_web: {"action":"search_web","target":"query"}
                        - set_brightness: {"action":"set_brightness","value":"0-100"}
                        - check_battery: {"action":"check_battery"}
                        - run_command: {"action":"run_command","target":"command_string"}
                        - toggle_bluetooth: {"action":"toggle_bluetooth","value":"on/off"}
                        - toggle_wifi: {"action":"toggle_wifi","value":"on/off"}
                        - get_system_info: {"action":"get_system_info","target":"cpu/memory/disk/network/all"}
                        - adjust_volume: {"action":"adjust_volume","value":"percentage","direction":"up/down/set"}
                        - media_control: {"action":"media_control","target":"play/pause/next/previous/stop"}
                        - take_screenshot: {"action":"take_screenshot"}
                        - open_file: {"action":"open_file","target":"file_path"}
                        - create_file: {"action":"create_file","target":"file_path","value":"content"}
                        - delete_file: {"action":"delete_file","target":"file_path"}
                        - list_directory: {"action":"list_directory","target":"directory_path"}
                        - check_process: {"action":"check_process","target":"process_name"}
                        - kill_process: {"action":"kill_process","target":"process_name_or_pid"}
                        - shutdown: {"action":"shutdown","value":"shutdown/restart/sleep"}
                        
                        Examples:
                        "Open Chrome" → {"type":"LOCAL","action":"open_app","target":"chrome"}
                        "Check battery" → {"type":"LOCAL","action":"check_battery"}
                        "What is Python?" → {"type":"AI","query":"What is Python?"}
                        "Run dir" → {"type":"LOCAL","action":"run_command","target":"dir"}
                        
                        For ANY system command not listed, use run_command with the actual command string.
                        Respond with ONLY valid JSON, no markdown.
                        
                        Command: %s
                        """, command, command);

                GenerateContentResponse response = geminiClient.models.generateContent(
                        "gemini-2.5-flash",
                        combinedPrompt,
                        null);

                String json = response.text().trim();
                // Clean JSON response - remove markdown code blocks if present
                if (json.startsWith("```")) {
                    int start = json.indexOf("{");
                    int end = json.lastIndexOf("}");
                    if (start >= 0 && end > start) {
                        json = json.substring(start, end + 1);
                    }
                }
                
                JsonObject obj;
                try {
                    obj = JsonParser.parseString(json).getAsJsonObject();
                } catch (Exception e) {
                    // If JSON parsing fails, try to execute as a direct command
                    System.out.println("Failed to parse JSON, attempting direct command execution: " + json);
                    String result = ActionExecutor.execute("run_command", command, null);
                    String confirmationPrompt = String.format(
                            "As Archer, confirm you executed the action: '%s'. Be concise and professional.",
                            result);
                    streamGeminiResponse("gemini-2.5-flash", confirmationPrompt, guiCallback);
                    return;
                }

                String type = obj.has("type") ? obj.get("type").getAsString() : "LOCAL";
                
                // Handle AI commands
                if (type.equalsIgnoreCase("AI")) {
                    String query = obj.has("query") ? obj.get("query").getAsString() : command;
                    streamGeminiResponse("gemini-2.5-flash", query, guiCallback);
                    return;
                }

                // Handle LOCAL commands
                String action = obj.has("action") ? obj.get("action").getAsString() : null;
                String target = obj.has("target") ? obj.get("target").getAsString() : null;
                String value = obj.has("value") ? obj.get("value").getAsString() : null;
                String direction = obj.has("direction") ? obj.get("direction").getAsString() : null;
                
                // For adjust_volume, use direction as target if present
                if (action != null && action.equals("adjust_volume") && direction != null) {
                    target = direction;
                }

                // execute local action
                String result = ActionExecutor.execute(action, target, value);

                // generate confirmation
                String confirmationPrompt = String.format(
                        "As Archer, confirm you executed the action: '%s'. Be concise and professional.",
                        result);

                streamGeminiResponse("gemini-2.5-flash", confirmationPrompt, guiCallback);

            } catch (Exception e) {
                e.printStackTrace();
                guiCallback.accept("Archer: Error while processing command: " + e.getMessage());
            }
        });
    }

    private void streamGeminiResponse(String model, String prompt, Consumer<String> guiCallback) {
        executor.submit(() -> {
            StringBuilder fullResponse = new StringBuilder();
            geminiClient.models.generateContentStream(model, prompt, null)
                    .forEach(responsePart -> {
                        String textChunk = responsePart.text();
                        if (textChunk != null && !textChunk.isEmpty()) {
                            fullResponse.append(textChunk);
                        }
                    });
            if (fullResponse.length() > 0) {
                guiCallback.accept("Archer: " + fullResponse.toString().trim());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
