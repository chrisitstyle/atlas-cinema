package com.atlascinema.screening.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MovieClientResponse(
        UUID id,
        Integer durationMinutes,
        boolean discontinued
) {
}
