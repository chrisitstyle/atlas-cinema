package com.atlascinema.screening.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record CreateScreeningRequest(

        @NotNull
        UUID movieId,

        @NotNull
        UUID hallId,

        @NotNull
        @Future
        LocalDateTime startsAt,

        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        BigDecimal basePrice

) {
}
