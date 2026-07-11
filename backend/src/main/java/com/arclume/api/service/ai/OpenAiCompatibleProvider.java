package com.arclume.api.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class OpenAiCompatibleProvider implements AiProvider {

    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final String apiKey;
    private final int timeoutMs;
    private final ObjectMapper objectMapper;

    public OpenAiCompatibleProvider(
            @Value("${app.ai.enabled:false}") boolean enabled,
            @Value("${app.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.model:gpt-4o-mini}") String model,
            @Value("${app.ai.api-key:}") String apiKey,
            @Value("${app.ai.timeout-ms:30000}") int timeoutMs) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.model = model;
        this.apiKey = apiKey;
        this.timeoutMs = timeoutMs;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String generateChatCompletion(String systemPrompt, String userPrompt) throws Exception {
        if (!enabled) {
            throw new IllegalStateException("AI processing is disabled by default");
        }

        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IllegalArgumentException("AI provider API key is missing");
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        ObjectNode payload = objectMapper.createObjectNode();
        payload.put("model", model);

        ArrayNode messages = objectMapper.createArrayNode();
        
        ObjectNode systemMsg = objectMapper.createObjectNode();
        systemMsg.put("role", "system");
        systemMsg.put("content", systemPrompt);
        messages.add(systemMsg);

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        userMsg.put("content", userPrompt);
        messages.add(userMsg);

        payload.set("messages", messages);

        ObjectNode responseFormat = objectMapper.createObjectNode();
        responseFormat.put("type", "json_object");
        payload.set("response_format", responseFormat);

        String jsonPayload = objectMapper.writeValueAsString(payload);

        String url = baseUrl;
        if (!url.endsWith("/")) {
            url += "/";
        }
        url += "chat/completions";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new RuntimeException("AI provider error: Received HTTP " + response.statusCode());
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            return message.path("content").asText();
        }

        throw new RuntimeException("AI provider returned empty or malformed completion response");
    }
}
