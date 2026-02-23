package com.searchengine.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "search")
public class SearchProperties {

    private Embedding embedding = new Embedding();
    private Llm llm = new Llm();
    private Jina jina = new Jina();
    private RateLimit rateLimit = new RateLimit();
    private Cache cache = new Cache();
    private Providers providers = new Providers();
    private Enrichment enrichment = new Enrichment();

    public Embedding getEmbedding() { return embedding; }
    public void setEmbedding(Embedding embedding) { this.embedding = embedding; }

    public Llm getLlm() { return llm; }
    public void setLlm(Llm llm) { this.llm = llm; }

    public Jina getJina() { return jina; }
    public void setJina(Jina jina) { this.jina = jina; }

    public RateLimit getRateLimit() { return rateLimit; }
    public void setRateLimit(RateLimit rateLimit) { this.rateLimit = rateLimit; }

    public Cache getCache() { return cache; }
    public void setCache(Cache cache) { this.cache = cache; }

    public Providers getProviders() { return providers; }
    public void setProviders(Providers providers) { this.providers = providers; }

    public Enrichment getEnrichment() { return enrichment; }
    public void setEnrichment(Enrichment enrichment) { this.enrichment = enrichment; }

    public static class Embedding {
        private boolean enabled = false;
        private String provider = "mistral";
        private String apiKey = "";
        private String model = "mistral-embed";
        private int dimensions = 1024;
        private long timeoutMillis = 5000;
        private int batchSize = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getDimensions() { return dimensions; }
        public void setDimensions(int dimensions) { this.dimensions = dimensions; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    }

    public static class Llm {
        private boolean enabled = false;
        private String baseUrl = "https://api.mistral.ai/v1";
        private String apiKey = "";
        private String model = "mistral-small-latest";
        private int maxTokens = 512;
        private int timeoutSeconds = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
        public int getTimeoutSeconds() { return timeoutSeconds; }
        public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    }

    public static class Jina {
        private String searchApiKey = "";
        private String searchUrl = "https://s.jina.ai";
        private long timeoutMillis = 8000;

        public String getSearchApiKey() { return searchApiKey; }
        public void setSearchApiKey(String searchApiKey) { this.searchApiKey = searchApiKey; }
        public String getSearchUrl() { return searchUrl; }
        public void setSearchUrl(String searchUrl) { this.searchUrl = searchUrl; }
        public long getTimeoutMillis() { return timeoutMillis; }
        public void setTimeoutMillis(long timeoutMillis) { this.timeoutMillis = timeoutMillis; }
    }

    public static class RateLimit {
        private int maxConcurrent = 100;
        private int maxRequestsPerMinute = 30;
        private boolean botCheckEnabled = true;

        public int getMaxConcurrent() { return maxConcurrent; }
        public void setMaxConcurrent(int maxConcurrent) { this.maxConcurrent = maxConcurrent; }
        public int getMaxRequestsPerMinute() { return maxRequestsPerMinute; }
        public void setMaxRequestsPerMinute(int maxRequestsPerMinute) { this.maxRequestsPerMinute = maxRequestsPerMinute; }
        public boolean isBotCheckEnabled() { return botCheckEnabled; }
        public void setBotCheckEnabled(boolean botCheckEnabled) { this.botCheckEnabled = botCheckEnabled; }
    }

    public static class Cache {
        private long searchTtlMinutes = 10;

        public long getSearchTtlMinutes() { return searchTtlMinutes; }
        public void setSearchTtlMinutes(long searchTtlMinutes) { this.searchTtlMinutes = searchTtlMinutes; }
    }

    public static class Providers {
        private Stackoverflow stackoverflow = new Stackoverflow();

        public Stackoverflow getStackoverflow() { return stackoverflow; }
        public void setStackoverflow(Stackoverflow stackoverflow) { this.stackoverflow = stackoverflow; }

        public static class Stackoverflow {
            private String baseUrl = "https://api.stackexchange.com";
            private String apiKey = "";

            public String getBaseUrl() { return baseUrl; }
            public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
            public String getApiKey() { return apiKey; }
            public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        }
    }

    public static class Enrichment {
        private boolean enabled = true;
        private int topN = 5;
        private int maxConcurrentFetches = 20;
        private int perHostLimit = 5;
        private long fetchTimeoutMillis = 4000;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getTopN() { return topN; }
        public void setTopN(int topN) { this.topN = topN; }
        public int getMaxConcurrentFetches() { return maxConcurrentFetches; }
        public void setMaxConcurrentFetches(int maxConcurrentFetches) { this.maxConcurrentFetches = maxConcurrentFetches; }
        public int getPerHostLimit() { return perHostLimit; }
        public void setPerHostLimit(int perHostLimit) { this.perHostLimit = perHostLimit; }
        public long getFetchTimeoutMillis() { return fetchTimeoutMillis; }
        public void setFetchTimeoutMillis(long fetchTimeoutMillis) { this.fetchTimeoutMillis = fetchTimeoutMillis; }
    }
}
