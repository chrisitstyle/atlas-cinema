package com.atlascinema.screening.controller;

import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.service.ScreeningService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

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

    @GetMapping
    public List<ScreeningResponse> getAllScreenings(
            @RequestParam(required = false) UUID movieId,
            @RequestParam(required = false) UUID hallId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date
    ) {
        return screeningService.getScreenings(movieId,hallId,date);
    }
}
