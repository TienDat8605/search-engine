package com.searchengine.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

@Component
public class StackExchangeBackoffManager {

    private final AtomicLong blockedUntilMillis = new AtomicLong(0L);
    // Initialised to MAX_VALUE so we don't treat missing quota info as exhausted
    private final AtomicInteger quotaRemaining = new AtomicInteger(Integer.MAX_VALUE);

    public boolean isBackoffActive() {
        return System.currentTimeMillis() < blockedUntilMillis.get();
    }

    public boolean isQuotaExhausted() {
        return quotaRemaining.get() == 0;
    }

    public Duration remainingBackoff() {
        long remaining = blockedUntilMillis.get() - System.currentTimeMillis();
        if (remaining <= 0) {
            return Duration.ZERO;
        }
        return Duration.ofMillis(remaining);
    }

    public void registerFromResponse(JsonNode response) {
        if (response == null) {
            return;
        }
        long backoffSeconds = response.path("backoff").asLong(0L);
        if (backoffSeconds > 0) {
            long candidateUntil = Instant.now().plusSeconds(backoffSeconds).toEpochMilli();
            blockedUntilMillis.updateAndGet(current -> Math.max(current, candidateUntil));
        }
        if (response.has("quota_remaining")) {
            quotaRemaining.set(response.path("quota_remaining").asInt(Integer.MAX_VALUE));
        }
    }
}
