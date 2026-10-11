package com.atlascinema.cinema.dto;

import com.atlascinema.cinema.domain.SeatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateSeatRequest(

        @NotBlank
        @Size(max = 10)
        String row,

        @NotNull
        @Positive
        Integer number,

        @NotNull
        SeatType type
) {
}
