package com.atlascinema.cinema.dto;

import com.atlascinema.cinema.domain.Hall;

import java.util.List;
import java.util.UUID;

public record HallStructureResponse(
        UUID id,
        String name,
        List<SeatResponse> seats
) {

    public static HallStructureResponse from(Hall hall) {
        return new HallStructureResponse(
                hall.getId(),
                hall.getName(),
                hall.getSeats()
                        .stream()
                        .map(SeatResponse::from)
                        .toList());
    }
}
