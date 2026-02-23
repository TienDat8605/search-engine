package com.searchengine.service;

import com.searchengine.api.dto.AiOverviewResponse;
import com.searchengine.integration.LlmClient;
import com.searchengine.persistence.DocumentEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiOverviewService {

    private static final Logger log = LoggerFactory.getLogger(AiOverviewService.class);
    private static final int MAX_SOURCES = 5;
    private static final int MAX_CONTENT_CHARS = 600;
    // Matches [SO-1], [SO-12], etc.
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[SO-(\\d+)]");

    private static final String SYSTEM_PROMPT = """
            You are a technical assistant that answers programming questions using only the provided Stack Overflow sources.
            Rules:
            - Answer using ONLY the information from the numbered sources provided.
            - Cite every factual claim with [SO-n] immediately after the claim, where n is the source number.
            - If a claim draws from multiple sources, cite all of them: [SO-1][SO-3].
            - If the sources do not contain enough information to answer reliably, respond exactly with: "I don't have enough information from these sources."
            - Do not use your own parametric knowledge. Do not hallucinate.
            - Keep the answer concise: 3-5 sentences maximum.
            - Do not use markdown headers or bullet points. Write in plain prose.
            """;

    private final LlmClient llmClient;

    public AiOverviewService(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public boolean isEnabled() {
        return llmClient.isEnabled();
    }

    /**
     * Generates an AI overview grounded in the provided enriched documents.
     * Only documents with non-blank bestAnswerText are used (cold-start safe).
     *
     * @param query the user's search query
     * @param candidates enriched DocumentEntity results (top-N, ordered by rank)
     * @return AiOverviewResponse with HTML-safe answer and citation list, or empty on failure
     */
    public AiOverviewResponse generate(String query, List<DocumentEntity> candidates) {
        List<DocumentEntity> enriched = candidates.stream()
                .filter(d -> d.getBestAnswerText() != null && !d.getBestAnswerText().isBlank())
                .limit(MAX_SOURCES)
                .toList();

        if (enriched.isEmpty()) {
            log.debug("No enriched documents available for AI overview, skipping");
            return AiOverviewResponse.empty();
        }

        // Build citation map: 1-indexed source number → document
        Map<Integer, DocumentEntity> citationMap = new HashMap<>();
        StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Sources:\n\n");

        for (int i = 0; i < enriched.size(); i++) {
            int n = i + 1;
            DocumentEntity doc = enriched.get(i);
            citationMap.put(n, doc);

            String content = doc.getBestAnswerText().length() > MAX_CONTENT_CHARS
                    ? doc.getBestAnswerText().substring(0, MAX_CONTENT_CHARS) + "..."
                    : doc.getBestAnswerText();

            userPrompt.append("[SO-").append(n).append("] Title: \"").append(doc.getTitle()).append("\"\n");
            userPrompt.append("Content: ").append(content).append("\n\n");
        }

        userPrompt.append("Question: ").append(query);

        String raw = llmClient.generate(SYSTEM_PROMPT, userPrompt.toString());
        if (raw == null || raw.isBlank()
                || raw.contains("I don't have enough information from these sources")) {
            return AiOverviewResponse.empty();
        }

        return buildResponse(raw, citationMap);
    }

    /**
     * Replaces [SO-n] markers with inline anchor links and builds the citation footnotes list.
     * Sanitises LLM output to prevent XSS — only [SO-n] inline anchors are preserved.
     */
    private AiOverviewResponse buildResponse(String raw, Map<Integer, DocumentEntity> citationMap) {
        // First escape all HTML from LLM output to prevent XSS
        String escaped = HtmlUtils.htmlEscape(raw);

        // Then replace escaped [SO-n] markers with trusted anchor tags
        Matcher m = CITATION_PATTERN.matcher(escaped);
        StringBuffer answer = new StringBuffer();
        List<Integer> referencedNums = new ArrayList<>();

        while (m.find()) {
            int n = Integer.parseInt(m.group(1));
            if (citationMap.containsKey(n)) {
                DocumentEntity doc = citationMap.get(n);
                long qId = doc.getQuestionId() != null ? doc.getQuestionId() : 0;
                String link = "<a href=\"#q-" + qId + "\" class=\"ai-citation\">[" + n + "]</a>";
                m.appendReplacement(answer, Matcher.quoteReplacement(link));
                if (!referencedNums.contains(n)) referencedNums.add(n);
            } else {
                m.appendReplacement(answer, Matcher.quoteReplacement(m.group(0)));
            }
        }
        m.appendTail(answer);

        // Build citation footnotes only for actually-referenced sources
        List<AiOverviewResponse.Citation> citations = referencedNums.stream()
                .filter(citationMap::containsKey)
                .map(n -> {
                    DocumentEntity doc = citationMap.get(n);
                    return new AiOverviewResponse.Citation(doc.getQuestionId(), doc.getTitle(), doc.getUrl());
                })
                .toList();

        return new AiOverviewResponse(answer.toString(), citations);
    }
}
