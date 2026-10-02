package com.atlascinema.cinema.controller;

import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.service.CinemaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    @PostMapping
    public ResponseEntity<CinemaResponse> createCinema(
            @Valid @RequestBody CreateCinemaRequest request
    ) {
        CinemaResponse cinema = cinemaService.createCinema(request);

        return ResponseEntity.created(URI.create("/cinemas/" + cinema.id()))
                .body(cinema);
    }

    @PostMapping("/{cinemaId}/halls")
    public ResponseEntity<HallResponse> addHall(
            @PathVariable UUID cinemaId,
            @Valid @RequestBody CreateHallRequest request
    ) {
        HallResponse hall = cinemaService.addHall(cinemaId, request);

        return ResponseEntity.created(
                        URI.create("/cinemas/" + cinemaId + "/halls/" + hall.id()))
                .body(hall);
    }

    @PostMapping("/{cinemaId}/halls/{hallId}/seats")
    public ResponseEntity<SeatResponse> addSeat(
            @PathVariable UUID cinemaId,
            @PathVariable UUID hallId,
            @Valid @RequestBody CreateSeatRequest request
    ) {
        SeatResponse seat = cinemaService.addSeat(cinemaId, hallId, request);

        return ResponseEntity
                .created(URI.create("/cinemas/"
                                        + cinemaId
                                        + "/halls/"
                                        + hallId
                                        + "/seats/"
                                        + seat.id()))
                .body(seat);
    }
}
