package com.atlascinema.cinema.controller;

import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.service.CinemaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
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

    @GetMapping("/{cinemaId}")
    public CinemaResponse getCinemaById(
            @PathVariable UUID cinemaId
    ) {
        return cinemaService.getCinemaById(cinemaId);
    }

    @GetMapping
    public List<CinemaResponse> getCinemas() {
        return cinemaService.getCinemas();
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

    @GetMapping("/{cinemaId}/halls")
    public List<HallResponse> getHalls(
            @PathVariable UUID cinemaId
    ) {
        return cinemaService.getHalls(cinemaId);
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

    @GetMapping("/{cinemaId}/halls/{hallId}/seats/{seatId}")
    public SeatResponse getSeatById(
            @PathVariable UUID cinemaId,
            @PathVariable UUID hallId,
            @PathVariable UUID seatId
    ) {
        return cinemaService.getSeatById(cinemaId, hallId, seatId);
    }

    @GetMapping("/{cinemaId}/halls/{hallId}/seats")
    public List<SeatResponse> getSeats(@PathVariable UUID cinemaId, @PathVariable UUID hallId) {
        return cinemaService.getSeats(cinemaId, hallId);}

    @GetMapping("/{cinemaId}/structure")
    public CinemaStructureResponse getCinemaStructure(@PathVariable UUID cinemaId) {
        return cinemaService.getCinemaStructure(cinemaId);
    }
}
