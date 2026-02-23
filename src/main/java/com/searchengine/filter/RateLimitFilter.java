package com.searchengine.filter;

import com.searchengine.config.SearchProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP sliding window rate limiter (default 30 req/min) + bot User-Agent detection.
 * Applies only to /api/** paths.
 */
@Component
@Order(2)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private static final Set<String> BOT_UA_FRAGMENTS = Set.of(
            "python-requests", "python-urllib", "scrapy", "go-http", "wget/", "java/",
            "libwww", "curl/", "okhttp", "node-fetch", "axios", "httpie"
    );

    private final int maxRequestsPerMinute;
    private final boolean botCheckEnabled;
    private final Map<String, Deque<Long>> windowByIp = new ConcurrentHashMap<>();

    public RateLimitFilter(SearchProperties props) {
        this.maxRequestsPerMinute = props.getRateLimit().getMaxRequestsPerMinute();
        this.botCheckEnabled = props.getRateLimit().isBotCheckEnabled();
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        if (!httpReq.getRequestURI().startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }

        if (botCheckEnabled && isBot(httpReq)) {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(HttpServletResponse.SC_FORBIDDEN);
            httpRes.getWriter().write("{\"error\":\"Forbidden\"}");
            return;
        }

        String ip = resolveIp(httpReq);
        if (isRateLimited(ip)) {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(429);
            httpRes.setHeader("Retry-After", "60");
            httpRes.getWriter().write("{\"error\":\"Rate limit exceeded\"}");
            log.debug("Rate limited IP: {}", ip);
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isBot(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua == null || ua.isBlank()) return true;
        String lower = ua.toLowerCase();
        return BOT_UA_FRAGMENTS.stream().anyMatch(lower::contains);
    }

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        long windowStart = now - 60_000L;
        Deque<Long> timestamps = windowByIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < windowStart) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequestsPerMinute) return true;
            timestamps.addLast(now);
            return false;
        }
    }

    private String resolveIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    // Clean up stale IP entries every 5 minutes to prevent memory leak
    @Scheduled(fixedDelay = 300_000)
    public void evictStaleEntries() {
        long windowStart = System.currentTimeMillis() - 60_000L;
        windowByIp.entrySet().removeIf(entry -> {
            Deque<Long> ts = entry.getValue();
            synchronized (ts) {
                while (!ts.isEmpty() && ts.peekFirst() < windowStart) ts.pollFirst();
                return ts.isEmpty();
            }
        });
    }
}
