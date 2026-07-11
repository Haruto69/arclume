package com.arclume.api.service.ai;

public interface AiProvider {
    String generateChatCompletion(String systemPrompt, String userPrompt) throws Exception;
}
