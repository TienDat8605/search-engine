package com.searchengine.integration;

public interface LlmClient {

    /** Sends a chat completion request and returns the assistant's text response. Returns null on failure. */
    String complete(String systemPrompt, String userPrompt);

    boolean isEnabled();
}
