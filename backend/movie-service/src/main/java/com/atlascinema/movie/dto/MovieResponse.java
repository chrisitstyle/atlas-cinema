package com.atlascinema.movie.dto;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import com.atlascinema.movie.domain.Movie;

import java.time.LocalDate;
import java.util.UUID;

public record MovieResponse(
        UUID id,
        String title,
        String description,
        Integer durationMinutes,
        AgeRating ageRating,
        Genre genre,
        LocalDate releaseDate,
        String posterUrl,
        String trailerUrl,
        boolean discontinued
) {

    public static MovieResponse from(Movie movie) {
        return new MovieResponse(
                movie.getId(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getDurationMinutes(),
                movie.getAgeRating(),
                movie.getGenre(),
                movie.getReleaseDate(),
                movie.getPosterUrl(),
                movie.getTrailerUrl(),
                movie.isDiscontinued()
        );
    }
}
