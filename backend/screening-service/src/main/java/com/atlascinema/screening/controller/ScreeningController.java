package com.atlascinema.screening.controller;

import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.service.ScreeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/screenings")
@RequiredArgsConstructor
public class ScreeningController {

    private final ScreeningService screeningService;

    @PostMapping
    public ResponseEntity<ScreeningResponse> createScreening(
            @Valid @RequestBody CreateScreeningRequest request
    ) {
        ScreeningResponse screening = screeningService.createScreening(request);

        return ResponseEntity
                .created(URI.create("/screenings/" + screening.id()))
                .body(screening);
    }
}
