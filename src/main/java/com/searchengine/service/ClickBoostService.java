package com.searchengine.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.searchengine.persistence.ClickEventRepository;

@Service
public class ClickBoostService {

    private static final Logger log = LoggerFactory.getLogger(ClickBoostService.class);

    /**
     * Additive boost weight per log-unit of clicks.
     * 1 click → +0.10, 3 clicks → +0.17, 10 clicks → +0.24, 50+ clicks → capped at MAX_BOOST.
     */
    private static final double BOOST_WEIGHT = 0.10;
    private static final double MAX_BOOST = 0.5;
    private static final int LOOKBACK_DAYS = 30;

    private final ClickEventRepository clickEventRepository;

    public ClickBoostService(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    /**
     * Returns a URL→boost map for the given query.
     * Only URLs present in {@code candidateUrls} are considered.
     * Returns an empty map on any error so ranking is never blocked.
     */
    public Map<String, Double> getBoosts(String normalizedQuery, List<String> candidateUrls) {
        if (normalizedQuery == null || normalizedQuery.isBlank() || candidateUrls == null || candidateUrls.isEmpty()) {
            return Map.of();
        }
        try {
            Instant since = Instant.now().minus(LOOKBACK_DAYS, ChronoUnit.DAYS);
            List<ClickEventRepository.UrlClickCount> counts =
                    clickEventRepository.countClicksForQueryAndUrls(normalizedQuery, candidateUrls, since);

            Map<String, Double> boosts = new HashMap<>();
            for (ClickEventRepository.UrlClickCount row : counts) {
                double boost = Math.min(MAX_BOOST, Math.log1p(row.getClicks()) * BOOST_WEIGHT);
                boosts.put(row.getUrl(), boost);
            }
            return boosts;
        } catch (Exception e) {
            log.debug("Click boost lookup failed for '{}': {}", normalizedQuery, e.getMessage());
            return Map.of();
        }
    }
}
