package com.atlascinema.screening.dto;

import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ScreeningResponse(
        UUID id,
        UUID movieId,
        UUID hallId,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        BigDecimal basePrice,
        ScreeningStatus status
) {

    public static ScreeningResponse from(Screening screening) {
        return new ScreeningResponse(
                screening.getId(),
                screening.getMovieId(),
                screening.getHallId(),
                screening.getStartsAt(),
                screening.getEndsAt(),
                screening.getBasePrice(),
                screening.getStatus());
    }
}
