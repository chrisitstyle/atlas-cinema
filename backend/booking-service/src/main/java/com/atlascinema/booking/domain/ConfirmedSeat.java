package com.atlascinema.booking.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/*
 * Records permanent ownership of one seat for one screening
 * The database unique constraint prevents selling the seat twice
 */
@Entity
@Table(name = "confirmed_seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_confirmed_seats_screening_seat",
                        columnNames = {"screening_id", "seat_id"}),
                @UniqueConstraint(
                        name = "uq_confirmed_seats_booking_seat",
                        columnNames = {"booking_id", "seat_id"})
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConfirmedSeat {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "screening_id", nullable = false, updatable = false)
    private UUID screeningId;

    @Column(name = "seat_id", nullable = false, updatable = false)
    private UUID seatId;

    @Column(name = "confirmed_at", nullable = false, updatable = false)
    private LocalDateTime confirmedAt;

    public static ConfirmedSeat create(
            UUID bookingId,
            UUID screeningId,
            UUID seatId,
            LocalDateTime confirmedAt
    ) {
        ConfirmedSeat confirmedSeat = new ConfirmedSeat();
        confirmedSeat.id = UUID.randomUUID();
        confirmedSeat.bookingId = Objects.requireNonNull(bookingId);
        confirmedSeat.screeningId = Objects.requireNonNull(screeningId);
        confirmedSeat.seatId = Objects.requireNonNull(seatId);
        confirmedSeat.confirmedAt = Objects.requireNonNull(confirmedAt);
        return confirmedSeat;
    }
}
