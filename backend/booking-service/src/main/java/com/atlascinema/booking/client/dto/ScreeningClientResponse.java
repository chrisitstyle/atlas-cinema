package com.atlascinema.booking.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScreeningClientResponse(
        UUID id,
        UUID hallId,
        LocalDateTime startsAt,
        BigDecimal basePrice,
        ScreeningClientStatus status
) {
}
