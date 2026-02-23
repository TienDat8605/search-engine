package com.searchengine.integration;

import java.util.List;

public interface EmbeddingClient {

    /** Returns a single embedding vector for the given text. Returns null on failure. */
    float[] embed(String text);

    /** Batch embed multiple texts. Returns list of same length, nulls for failures. */
    List<float[]> embedBatch(List<String> texts);

    boolean isEnabled();
}
