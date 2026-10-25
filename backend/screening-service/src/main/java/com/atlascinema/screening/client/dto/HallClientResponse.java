package com.atlascinema.screening.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HallClientResponse(
        UUID id,
        String name,
        UUID cinemaId
) {
}
