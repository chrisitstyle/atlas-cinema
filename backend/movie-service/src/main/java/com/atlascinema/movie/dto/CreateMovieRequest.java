package com.atlascinema.movie.dto;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateMovieRequest(

        @NotBlank
        @Size(max = 255)
        String title,

        String description,

        @NotNull
        @Positive
        Integer durationMinutes,

        @NotNull
        AgeRating ageRating,

        @NotNull
        Genre genre,

        @NotNull
        LocalDate releaseDate,

        @Size(max = 1000)
        String posterUrl,

        @Size(max = 1000)
        String trailerUrl
) { }
