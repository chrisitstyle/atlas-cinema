package com.atlascinema.booking.client;

import com.atlascinema.booking.client.dto.SeatClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "cinema-service")
public interface CinemaClient {

    @GetMapping("/internal/halls/{hallId}/seats/{seatId}")
    SeatClientResponse getSeat(
            @PathVariable("hallId")
            UUID hallId,

            @PathVariable("seatId")
            UUID seatId);
}
