package com.atlascinema.booking.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "booking_seats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BookingSeat {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "screening_id", nullable = false)
    private UUID screeningId;

    @Column(name = "seat_id", nullable = false)
    private UUID seatId;

    @Column(
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal price;

    private BookingSeat(
            UUID id,
            Booking booking,
            UUID seatId,
            BigDecimal price) {
        this.id = id;
        this.booking = booking;
        this.screeningId = booking.getScreeningId();
        this.seatId = seatId;
        this.price = price;
    }

    static BookingSeat create(
            Booking booking,
            UUID seatId,
            BigDecimal price) {
        return new BookingSeat(
                UUID.randomUUID(),
                booking,
                seatId,
                price);
    }
}