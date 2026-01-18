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
        System.out.println("CommandProcessor: processCommand() called with command: '" + command + "'");
        System.out.println("CommandProcessor: guiCallback is null? " + (guiCallback == null));
        System.out.println("CommandProcessor: geminiClient is null? " + (geminiClient == null));
        System.out.println("CommandProcessor: executor is null? " + (executor == null));
        
        if (guiCallback == null) {
            System.err.println("CommandProcessor: ERROR - guiCallback is null!");
            return;
        }
        
        if (geminiClient == null) {
            System.err.println("CommandProcessor: ERROR - geminiClient is null!");
            guiCallback.accept("Archer: Error - Gemini client not initialized. Check GEMINI_API_KEY.");
            return;
        }
        
        System.out.println("CommandProcessor: Submitting task to executor...");
        System.out.println("CommandProcessor: Executor shutdown? " + executor.isShutdown());
        System.out.println("CommandProcessor: Executor terminated? " + executor.isTerminated());
        
        executor.submit(() -> {
            System.out.println("CommandProcessor: ===== EXECUTOR THREAD STARTED =====");
            System.out.println("CommandProcessor: Thread name: " + Thread.currentThread().getName());
            try {
                System.out.println("CommandProcessor: Starting command processing in executor thread...");
                System.out.println("CommandProcessor: Command to process: '" + command + "'");
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

                System.out.println("CommandProcessor: About to call Gemini API...");
                System.out.println("CommandProcessor: Model: gemini-2.5-flash");
                System.out.println("CommandProcessor: Prompt length: " + combinedPrompt.length());
                System.out.println("CommandProcessor: Prompt preview: " + combinedPrompt.substring(0, Math.min(200, combinedPrompt.length())) + "...");
                
                GenerateContentResponse response = null;
                try {
                    System.out.println("CommandProcessor: Calling geminiClient.models.generateContent()...");
                    response = geminiClient.models.generateContent(
                            "gemini-2.5-flash",
                            combinedPrompt,
                            null);
                    System.out.println("CommandProcessor: Gemini API call SUCCESSFUL!");
                    System.out.println("CommandProcessor: Response is null? " + (response == null));
                } catch (Exception apiException) {
                    System.err.println("CommandProcessor: EXCEPTION calling Gemini API!");
                    System.err.println("CommandProcessor: Exception type: " + apiException.getClass().getName());
                    System.err.println("CommandProcessor: Exception message: " + apiException.getMessage());
                    apiException.printStackTrace();
                    throw apiException; // Re-throw to be caught by outer catch
                }
                
                if (response == null) {
                    throw new RuntimeException("Gemini API returned null response");
                }

                System.out.println("CommandProcessor: Getting text from response...");
                String json = null;
                try {
                    json = response.text().trim();
                    System.out.println("CommandProcessor: Received JSON from Gemini (length: " + json.length() + ")");
                    System.out.println("CommandProcessor: JSON content: " + json);
                } catch (Exception e) {
                    System.err.println("CommandProcessor: ERROR getting text from response: " + e.getMessage());
                    e.printStackTrace();
                    throw e;
                }
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
                    System.out.println("CommandProcessor: Parsed JSON successfully");
                } catch (Exception e) {
                    // If JSON parsing fails, try to execute as a direct command
                    System.out.println("CommandProcessor: Failed to parse JSON, attempting direct command execution: " + json);
                    String result = ActionExecutor.execute("run_command", command, null);
                    String confirmationPrompt = String.format(
                            "As Archer, confirm you executed the action: '%s'. Be concise and professional.",
                            result);
                    System.out.println("CommandProcessor: Streaming Gemini response for direct command...");
                    streamGeminiResponse("gemini-2.5-flash", confirmationPrompt, guiCallback);
                    return;
                }

                String type = obj.has("type") ? obj.get("type").getAsString() : "LOCAL";
                System.out.println("CommandProcessor: Command type: " + type);
                
                // Handle AI commands
                if (type.equalsIgnoreCase("AI")) {
                    String query = obj.has("query") ? obj.get("query").getAsString() : command;
                    System.out.println("CommandProcessor: Processing AI command, query: " + query);
                    streamGeminiResponse("gemini-2.5-flash", query, guiCallback);
                    return;
                }

                // Handle LOCAL commands
                String action = obj.has("action") ? obj.get("action").getAsString() : null;
                String target = obj.has("target") ? obj.get("target").getAsString() : null;
                String value = obj.has("value") ? obj.get("value").getAsString() : null;
                String direction = obj.has("direction") ? obj.get("direction").getAsString() : null;
                
                System.out.println("CommandProcessor: Executing LOCAL action: " + action + ", target: " + target);
                
                // For adjust_volume, use direction as target if present
                if (action != null && action.equals("adjust_volume") && direction != null) {
                    target = direction;
                }

                // execute local action
                String result = ActionExecutor.execute(action, target, value);
                System.out.println("CommandProcessor: Action executed, result: " + result);

                // generate confirmation
                String confirmationPrompt = String.format(
                        "As Archer, confirm you executed the action: '%s'. Be concise and professional.",
                        result);

                System.out.println("CommandProcessor: Streaming Gemini confirmation response...");
                streamGeminiResponse("gemini-2.5-flash", confirmationPrompt, guiCallback);

            } catch (Exception e) {
                System.err.println("CommandProcessor: ===== EXCEPTION IN EXECUTOR THREAD =====");
                System.err.println("CommandProcessor: Exception type: " + e.getClass().getName());
                System.err.println("CommandProcessor: Exception message: " + e.getMessage());
                e.printStackTrace();
                
                String errorMsg = "Archer: Error while processing command: " + e.getMessage();
                if (e.getCause() != null) {
                    errorMsg += " (Cause: " + e.getCause().getMessage() + ")";
                }
                guiCallback.accept(errorMsg);
            } finally {
                System.out.println("CommandProcessor: ===== EXECUTOR THREAD COMPLETED =====");
            }
        });
        System.out.println("CommandProcessor: Task submitted to executor, returning from processCommand()");
    }

    private void streamGeminiResponse(String model, String prompt, Consumer<String> guiCallback) {
        System.out.println("CommandProcessor: streamGeminiResponse called, model: " + model);
        if (guiCallback == null) {
            System.err.println("CommandProcessor: ERROR - guiCallback is null in streamGeminiResponse!");
            return;
        }
        executor.submit(() -> {
            StringBuilder fullResponse = new StringBuilder();
            
            try {
                System.out.println("CommandProcessor: Starting Gemini streaming...");
                geminiClient.models.generateContentStream(model, prompt, null)
                        .forEach(responsePart -> {
                            String textChunk = responsePart.text();
                            if (textChunk != null && !textChunk.isEmpty()) {
                                fullResponse.append(textChunk);
                                
                                // Stream accumulated text in real-time
                                String responseText = "Archer: " + fullResponse.toString();
                                System.out.println("CommandProcessor: Streaming chunk, total length: " + fullResponse.length());
                                guiCallback.accept(responseText);
                            }
                        });
                
                // Send final complete response (trimmed)
                if (fullResponse.length() > 0) {
                    String finalResponse = "Archer: " + fullResponse.toString().trim();
                    System.out.println("CommandProcessor: Sending final response: " + finalResponse.substring(0, Math.min(100, finalResponse.length())) + "...");
                    guiCallback.accept(finalResponse);
                } else {
                    System.out.println("CommandProcessor: WARNING - No response generated from Gemini!");
                    guiCallback.accept("Archer: I received your command but couldn't generate a response.");
                }
            } catch (Exception e) {
                System.err.println("CommandProcessor: Error streaming Gemini response: " + e.getMessage());
                e.printStackTrace();
                guiCallback.accept("Archer: Error generating response: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        executor.shutdown();
    }
}
