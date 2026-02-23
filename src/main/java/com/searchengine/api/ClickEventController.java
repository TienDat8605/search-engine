package com.searchengine.api;

import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.searchengine.api.dto.ClickEventRequest;
import com.searchengine.persistence.ClickEventEntity;
import com.searchengine.persistence.ClickEventRepository;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
public class ClickEventController {

    private final ClickEventRepository clickEventRepository;

    public ClickEventController(ClickEventRepository clickEventRepository) {
        this.clickEventRepository = clickEventRepository;
    }

    /**
     * Records an implicit click signal fired silently by the frontend via sendBeacon.
     * Returns 204 No Content — the client never waits for this response.
     */
    @PostMapping("/click")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void recordClick(@RequestBody @Valid ClickEventRequest request) {
        ClickEventEntity event = new ClickEventEntity();
        event.setQueryText(normalizeQuery(request.query()));
        event.setUrl(request.url());
        event.setPosition(Math.max(0, request.position()));
        clickEventRepository.save(event);
    }

    private String normalizeQuery(String query) {
        return query.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
