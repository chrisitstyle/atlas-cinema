package com.atlascinema.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SeatClientResponse(
        UUID id,
        UUID hallId
) {
}
