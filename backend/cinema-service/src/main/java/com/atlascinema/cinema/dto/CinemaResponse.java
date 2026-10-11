package com.atlascinema.cinema.dto;

import com.atlascinema.cinema.domain.Cinema;

import java.util.UUID;

public record CinemaResponse(
        UUID id,
        String name,
        String city,
        String address,
        boolean active
) {

    public static CinemaResponse from(Cinema cinema) {
        return new CinemaResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getCity(),
                cinema.getAddress(),
                cinema.isActive()
        );
    }
}
