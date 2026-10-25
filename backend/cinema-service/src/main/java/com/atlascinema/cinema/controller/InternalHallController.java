package com.atlascinema.cinema.controller;

import com.atlascinema.cinema.dto.HallResponse;
import com.atlascinema.cinema.dto.SeatResponse;
import com.atlascinema.cinema.service.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/halls")
@RequiredArgsConstructor
public class InternalHallController {

    private final CinemaService cinemaService;

    @GetMapping("/{hallId}")
    public HallResponse getHallById(@PathVariable UUID hallId) {
        return cinemaService.getHallById(hallId);}

    @GetMapping("/{hallId}/seats/{seatId}")
    public SeatResponse getSeatById(
            @PathVariable UUID hallId,
            @PathVariable UUID seatId) {
        return cinemaService.getSeatById(hallId, seatId);
    }
}
