package com.searchengine.api;

import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.searchengine.api.dto.AiOverviewResponse;
import com.searchengine.service.AiOverviewService;

import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api")
@Validated
public class AiOverviewController {

    private final AiOverviewService aiOverviewService;

    public AiOverviewController(AiOverviewService aiOverviewService) {
        this.aiOverviewService = aiOverviewService;
    }

    @GetMapping("/ask")
    public AiOverviewResponse ask(@RequestParam("q") @NotBlank String query) {
        if (!aiOverviewService.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "AI overview is not available.");
        }
        return aiOverviewService.generateOverview(query);
    }
}
