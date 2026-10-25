package com.atlascinema.screening.client;

import com.atlascinema.screening.client.dto.HallClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "cinema-service")
public interface CinemaClient {

    @GetMapping("/internal/halls/{hallId}")
    HallClientResponse getHall(@PathVariable("hallId") UUID hallId);
}
