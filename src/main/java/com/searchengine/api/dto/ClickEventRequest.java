package com.searchengine.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ClickEventRequest(
        @NotBlank @Size(max = 512) String query,
        @NotBlank @Size(max = 1024) String url,
        int position
) {
}
