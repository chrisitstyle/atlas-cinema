package com.atlascinema.booking.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Describes a ticket purchase without accepting an untrusted customer identifier.
 * Authenticated customer ownership is resolved from the verified JWT instead.
 */
public record CreateBookingRequest(
        @NotBlank @Email String contactEmail,
        @NotNull UUID screeningId,
        @NotEmpty List<@NotNull UUID> seatIds
) {
}
