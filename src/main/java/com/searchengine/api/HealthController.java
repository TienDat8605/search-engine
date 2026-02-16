package com.searchengine.api;

import java.time.Instant;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.searchengine.integration.StackExchangeBackoffManager;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final StackExchangeBackoffManager backoffManager;

    public HealthController(StackExchangeBackoffManager backoffManager) {
        this.backoffManager = backoffManager;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "timestamp", Instant.now().toString(),
                "stackexchangeBackoffActive", backoffManager.isBackoffActive(),
                "stackexchangeBackoffRemainingSeconds", backoffManager.remainingBackoff().toSeconds()
        );
    }
}
