package com.atlascinema.cinema.dto;

import com.atlascinema.cinema.domain.Cinema;

import java.util.List;
import java.util.UUID;

public record CinemaStructureResponse(
        UUID id,
        String name,
        String city,
        String address,
        boolean active,
        List<HallStructureResponse> halls
) {

    public static CinemaStructureResponse from(Cinema cinema) {
        return new CinemaStructureResponse(
                cinema.getId(),
                cinema.getName(),
                cinema.getCity(),
                cinema.getAddress(),
                cinema.isActive(),
                cinema.getHalls()
                        .stream()
                        .map(HallStructureResponse::from)
                        .toList()
        );
    }
}
