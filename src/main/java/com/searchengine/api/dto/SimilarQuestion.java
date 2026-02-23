package com.searchengine.api.dto;

import java.util.List;

public record SimilarQuestion(Long questionId, String title, String link, String snippet, List<String> tags) {}
