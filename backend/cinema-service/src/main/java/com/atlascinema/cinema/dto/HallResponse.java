package com.atlascinema.cinema.dto;

import com.atlascinema.cinema.domain.Hall;

import java.util.UUID;

public record HallResponse(
        UUID id,
        String name,
        UUID cinemaId
) {

    public static HallResponse from(Hall hall) {
        return new HallResponse(
                hall.getId(),
                hall.getName(),
                hall.getCinema().getId()
        );
    }
}
