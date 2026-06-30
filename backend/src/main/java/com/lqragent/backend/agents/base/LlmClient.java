package com.lqragent.backend.agents.base;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lqragent.backend.systemconfig.AppRuntimeConfig;
import com.lqragent.backend.agents.base.ToolCall;

import lombok.extern.slf4j.Slf4j;

/**
 * LLM 客户端
 * 通过 DeepSeek API 调用 LLM，支持 tool_calls
 */
@Slf4j
@Component
public class LlmClient {

    private final AppRuntimeConfig config;
    private final ObjectMapper mapper = new ObjectMapper();

    public LlmClient(AppRuntimeConfig config) {
        this.config = config;
    }

    /**
     * 调用 LLM（带工具）
     */
    public LlmResponse chat(
            String systemPrompt,
            List<Map<String, Object>> messages,
            List<Map<String, Object>> tools
    ) {
        ApiSettings api = resolveApiSettings();
        Map<String, Object> requestBody = buildRequestBody(api.model(), systemPrompt, messages, false);
        if (tools != null && !tools.isEmpty()) {
            requestBody.put("tools", tools);
            requestBody.put("tool_choice", "auto");
        }

        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            log.info("[LlmClient] calling LLM: host={}, model={}, apiKey={}...", api.baseUrl(), api.model(),
                    maskApiKey(api.apiKey()));

            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
            connManager.setMaxTotal(20);
            connManager.setDefaultMaxPerRoute(10);
            connManager.setDefaultConnectionConfig(ConnectionConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(10))
                    .setSocketTimeout(Timeout.ofSeconds(30))
                    .build());

            HttpComponentsClientHttpRequestFactory requestFactory =
                    new HttpComponentsClientHttpRequestFactory(
                            HttpClients.custom()
                                    .setConnectionManager(connManager)
                                    .evictExpiredConnections()
                                    .evictIdleConnections(TimeValue.ofSeconds(30))
                                    .build());

            String response = RestClient.builder()
                    .requestFactory(requestFactory)
                    .build()
                    .post()
                    .uri(api.baseUrl() + "/chat/completions")
                    .header("Authorization", "Bearer " + api.apiKey())
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .retrieve()
                    .body(String.class);

            log.info("[LlmClient] response received, length={}", response != null ? response.length() : 0);
            return parseResponse(response);

        } catch (Exception e) {
            log.error("[LlmClient] call failed: {}", e.getMessage(), e);
            String preview = requestBody.toString();
            if (preview.length() > 500) {
                preview = preview.substring(0, 500) + "...";
            }
            log.error("[LlmClient] request body preview: {}", preview);
            return new LlmResponse(null, null, "LLM call failed: " + e.getMessage());
        }
    }

    /**
     * 流式调用 LLM（SSE delta.content）；含 tool_calls 的场景请仍用 {@link #chat}。
     */
    public LlmResponse chatStream(
            String systemPrompt,
            List<Map<String, Object>> messages,
            StreamSink sink
    ) {
        ApiSettings api = resolveApiSettings();
        Map<String, Object> requestBody = buildRequestBody(api.model(), systemPrompt, messages, true);

        try {
            String jsonBody = mapper.writeValueAsString(requestBody);
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(api.baseUrl() + "/chat/completions"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Authorization", "Bearer " + api.apiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<java.io.InputStream> response = client.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() >= 400) {
                String errBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                return new LlmResponse(null, null, "LLM stream HTTP " + response.statusCode() + ": " + errBody);
            }

            StringBuilder fullContent = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode root = mapper.readTree(data);
                    JsonNode choices = root.path("choices");
                    if (choices.isEmpty()) {
                        continue;
                    }
                    String delta = choices.get(0).path("delta").path("content").asText("");
                    if (!delta.isEmpty()) {
                        fullContent.append(delta);
                        if (sink != null) {
                            sink.onChunk(delta);
                        }
                    }
                }
            }
            return new LlmResponse(fullContent.toString(), List.of(), null);
        } catch (Exception e) {
            log.error("[LlmClient] stream failed: {}", e.getMessage(), e);
            return new LlmResponse(null, null, "LLM stream failed: " + e.getMessage());
        }
    }

    /**
     * 简单调用（无工具）
     */
    public String chatSimple(String systemPrompt, String userMessage) {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "user", "content", userMessage)
        );
        LlmResponse response = chat(systemPrompt, messages, null);
        return response.content();
    }

    private ApiSettings resolveApiSettings() {
        String apiKey = config.get("llm.api-key", "");
        String baseUrl = config.get("llm.host", "https://api.deepseek.com");
        String model = config.get("llm.model", "deepseek-chat");
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return new ApiSettings(apiKey, baseUrl, model);
    }

    private Map<String, Object> buildRequestBody(
            String model,
            String systemPrompt,
            List<Map<String, Object>> messages,
            boolean stream
    ) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", model);
        requestBody.put("messages", buildAllMessages(systemPrompt, messages));
        requestBody.put("max_tokens", 4096);
        requestBody.put("temperature", 0.1);
        if (stream) {
            requestBody.put("stream", true);
        }
        return requestBody;
    }

    private List<Map<String, Object>> buildAllMessages(String systemPrompt, List<Map<String, Object>> messages) {
        List<Map<String, Object>> allMessages = new ArrayList<>();
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("role", "system");
        system.put("type", "system");
        system.put("content", systemPrompt);
        allMessages.add(system);
        for (Map<String, Object> msg : messages) {
            Map<String, Object> m = new LinkedHashMap<>(msg);
            if (!m.containsKey("type") && m.containsKey("role")) {
                m.put("type", m.get("role"));
            }
            allMessages.add(m);
        }
        return allMessages;
    }

    private static String maskApiKey(String apiKey) {
        if (apiKey.length() > 8) {
            return apiKey.substring(0, 4) + "****" + apiKey.substring(apiKey.length() - 4);
        }
        return "(short)";
    }

    private LlmResponse parseResponse(String responseJson) {
        try {
            JsonNode root = mapper.readTree(responseJson);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty()) {
                return new LlmResponse(null, null, "No choices in response");
            }

            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asText(null);

            List<ToolCall> toolCalls = new ArrayList<>();
            JsonNode toolCallsNode = message.path("tool_calls");
            if (toolCallsNode.isArray()) {
                for (JsonNode tc : toolCallsNode) {
                    String id = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    String argsJson = tc.path("function").path("arguments").asText("{}");
                    toolCalls.add(new ToolCall(id, name, argsJson));
                }
            }

            return new LlmResponse(content, toolCalls, null);

        } catch (Exception e) {
            log.error("[LlmClient] parse failed: {}", e.getMessage());
            return new LlmResponse(null, null, "Parse failed: " + e.getMessage());
        }
    }

    private static final class ApiSettings {
        private final String apiKey;
        private final String baseUrl;
        private final String model;

        private ApiSettings(String apiKey, String baseUrl, String model) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.model = model;
        }

        private String apiKey() {
            return apiKey;
        }

        private String baseUrl() {
            return baseUrl;
        }

        private String model() {
            return model;
        }
    }
}
