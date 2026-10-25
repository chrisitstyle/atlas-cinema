package com.atlascinema.screening.client;

import com.atlascinema.screening.client.dto.MovieClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "movie-service")
public interface MovieClient {

    @GetMapping("/movies/{movieId}")
    MovieClientResponse getMovie(@PathVariable("movieId") UUID movieId);
}