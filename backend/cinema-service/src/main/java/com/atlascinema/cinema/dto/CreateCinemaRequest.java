package com.atlascinema.cinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCinemaRequest(

        @NotBlank
        @Size(max = 255)
        String name,

        @NotBlank
        @Size(max = 120)
        String city,

        @NotBlank
        @Size(max = 500)
        String address
) {
}
