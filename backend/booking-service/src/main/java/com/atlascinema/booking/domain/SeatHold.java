package com.atlascinema.booking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "seat_holds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatHold {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY,
            optional = false)
    @JoinColumn(name = "booking_id",
            nullable = false)
    private Booking booking;

    @Column(name = "screening_id",
            nullable = false)
    private UUID screeningId;

    @Column(name = "seat_id",
            nullable = false)
    private UUID seatId;

    @Column(name = "expires_at",
            nullable = false)
    private LocalDateTime expiresAt;

    private SeatHold(
            UUID id,
            Booking booking,
            UUID screeningId,
            UUID seatId,
            LocalDateTime expiresAt
    ) {
        this.id = id;
        this.booking = booking;
        this.screeningId = screeningId;
        this.seatId = seatId;
        this.expiresAt = expiresAt;
    }

     static SeatHold create(
            Booking booking,
            UUID seatId,
            LocalDateTime expiresAt) {
        return new SeatHold(
                UUID.randomUUID(),
                booking,
                booking.getScreeningId(),
                seatId,
                expiresAt);
    }
}
