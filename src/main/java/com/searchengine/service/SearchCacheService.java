package com.searchengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.searchengine.api.dto.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

@Service
public class SearchCacheService {

    private static final Logger log = LoggerFactory.getLogger(SearchCacheService.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public SearchCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${search.cache.search-ttl-minutes:10}") long ttlMinutes
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofMinutes(ttlMinutes);
    }

    public Optional<SearchResponse> get(String key) {
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null || value.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, SearchResponse.class));
        } catch (IOException e) {
            log.warn("Failed to deserialize cached search response for key {}: {}", key, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Redis get failed for key {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String key, SearchResponse response) {
        try {
            String payload = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(key, payload, ttl);
        } catch (Exception e) {
            log.warn("Redis put failed for key {}: {}", key, e.getMessage());
        }
    }
}
