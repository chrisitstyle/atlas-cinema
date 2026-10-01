package com.atlascinema.movie.controller;

import com.atlascinema.movie.dto.CreateMovieRequest;
import com.atlascinema.movie.dto.MovieResponse;
import com.atlascinema.movie.dto.UpdateMovieRequest;
import com.atlascinema.movie.service.MovieService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @PostMapping
    public ResponseEntity<MovieResponse> createMovie(
            @Valid @RequestBody CreateMovieRequest request
    ) {
        MovieResponse movie = movieService.createMovie(request);

        return ResponseEntity
                .created(URI.create("/movies/" + movie.id()))
                .body(movie);
    }

    @GetMapping("/{id}")
    public MovieResponse getMovie(@PathVariable UUID id) {

        return movieService.getMovieById(id);
    }
    @GetMapping
    public List<MovieResponse> getAllMovies() {
        return movieService.getAllMovies();}

    @PutMapping("/{id}")
    public MovieResponse updateMovie(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMovieRequest request
    ) {
        return movieService.updateMovie(id, request);
    }

    @PatchMapping("/{id}/discontinue")
    public MovieResponse discontinueMovie(@PathVariable UUID id) {
        return movieService.discontinueMovie(id);
    }

    @PatchMapping("/{id}/restore")
    public MovieResponse restoreMovie(@PathVariable UUID id) {
        return movieService.restoreMovie(id);
    }
}
