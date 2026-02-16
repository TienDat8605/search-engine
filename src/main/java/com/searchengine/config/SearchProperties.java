package com.searchengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private Cache cache = new Cache();
    private Providers providers = new Providers();
    private Enrichment enrichment = new Enrichment();

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Providers getProviders() {
        return providers;
    }

    public void setProviders(Providers providers) {
        this.providers = providers;
    }

    public Enrichment getEnrichment() {
        return enrichment;
    }

    public void setEnrichment(Enrichment enrichment) {
        this.enrichment = enrichment;
    }

    public static class Enrichment {
        private boolean enabled = true;
        private int topN = 5;
        private int maxConcurrentFetches = 20;
        private int perHostLimit = 5;
        private long fetchTimeoutMillis = 4000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getTopN() {
            return topN;
        }

        public void setTopN(int topN) {
            this.topN = topN;
        }

        public int getMaxConcurrentFetches() {
            return maxConcurrentFetches;
        }

        public void setMaxConcurrentFetches(int maxConcurrentFetches) {
            this.maxConcurrentFetches = maxConcurrentFetches;
        }

        public int getPerHostLimit() {
            return perHostLimit;
        }

        public void setPerHostLimit(int perHostLimit) {
            this.perHostLimit = perHostLimit;
        }

        public long getFetchTimeoutMillis() {
            return fetchTimeoutMillis;
        }

        public void setFetchTimeoutMillis(long fetchTimeoutMillis) {
            this.fetchTimeoutMillis = fetchTimeoutMillis;
        }
    }

    public static class Cache {
        private long searchTtlMinutes = 10;

        public long getSearchTtlMinutes() {
            return searchTtlMinutes;
        }

        public void setSearchTtlMinutes(long searchTtlMinutes) {
            this.searchTtlMinutes = searchTtlMinutes;
        }
    }

    public static class Providers {
        private Stackoverflow stackoverflow = new Stackoverflow();

        public Stackoverflow getStackoverflow() {
            return stackoverflow;
        }

        public void setStackoverflow(Stackoverflow stackoverflow) {
            this.stackoverflow = stackoverflow;
        }

        public static class Stackoverflow {
            private String baseUrl = "https://api.stackexchange.com";
            private String apiKey = "";

            public String getBaseUrl() {
                return baseUrl;
            }

            public void setBaseUrl(String baseUrl) {
                this.baseUrl = baseUrl;
            }

            public String getApiKey() {
                return apiKey;
            }

            public void setApiKey(String apiKey) {
                this.apiKey = apiKey;
            }
        }
    }
}
