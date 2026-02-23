package com.searchengine.integration;

public interface LlmClient {
    /** Generate a response given a system prompt and user prompt. Returns null on failure. */
    String generate(String systemPrompt, String userPrompt);

    boolean isEnabled();
}
