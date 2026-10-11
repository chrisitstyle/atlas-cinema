package com.atlascinema.cinema.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHallRequest(

        @NotBlank
        @Size(max = 100)
        String name) { }
