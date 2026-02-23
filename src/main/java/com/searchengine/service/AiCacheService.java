package com.searchengine.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.api.dto.AiOverviewResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Service
public class AiCacheService {

    private static final String KEY_PREFIX = "ask:v1:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public AiCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${search.cache.ai-overview-ttl-minutes:60}") long ttlMinutes) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<AiOverviewResponse> get(String query) {
        String value = redisTemplate.opsForValue().get(KEY_PREFIX + query);
        if (value == null || value.isBlank()) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(value, AiOverviewResponse.class));
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

    public void put(String query, AiOverviewResponse response) {
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + query, objectMapper.writeValueAsString(response), ttl);
        } catch (JsonProcessingException ignored) {
        }
    }
}
