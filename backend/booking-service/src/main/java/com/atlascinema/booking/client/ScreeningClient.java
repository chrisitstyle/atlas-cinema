package com.atlascinema.booking.client;

import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "screening-service")
public interface ScreeningClient {

    @GetMapping("/screenings/{screeningId}")
    ScreeningClientResponse getScreening(@PathVariable("screeningId") UUID screeningId);
}
