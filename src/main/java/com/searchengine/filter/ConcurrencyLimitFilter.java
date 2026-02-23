package com.searchengine.filter;

import com.searchengine.config.SearchProperties;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * Caps the number of concurrent in-flight requests to /api/** at maxConcurrent (default 100).
 * Returns 503 immediately when the semaphore is full so threads are never blocked.
 */
@Component
@Order(1)
public class ConcurrencyLimitFilter implements Filter {

    private final Semaphore semaphore;

    public ConcurrencyLimitFilter(SearchProperties props) {
        this.semaphore = new Semaphore(props.getRateLimit().getMaxConcurrent(), true);
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpReq = (HttpServletRequest) req;
        if (!httpReq.getRequestURI().startsWith("/api/")) {
            chain.doFilter(req, res);
            return;
        }
        if (!semaphore.tryAcquire()) {
            HttpServletResponse httpRes = (HttpServletResponse) res;
            httpRes.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            httpRes.getWriter().write("{\"error\":\"Too many concurrent requests\"}");
            return;
        }
        try {
            chain.doFilter(req, res);
        } finally {
            semaphore.release();
        }
    }
}
