package com.atlascinema.cinema.dto;

import com.atlascinema.cinema.domain.Seat;
import com.atlascinema.cinema.domain.SeatType;

import java.util.UUID;

public record SeatResponse(
        UUID id,
        UUID hallId,
        String row,
        Integer number,
        SeatType type
) {

    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.getId(),
                seat.getHall().getId(),
                seat.getRow(),
                seat.getNumber(),
                seat.getType()
        );
    }
}
