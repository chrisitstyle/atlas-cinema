package com.atlascinema.booking.domain;

import com.atlascinema.booking.exception.BookingCancellationNotAllowedException;
import com.atlascinema.booking.exception.SeatAlreadyAddedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "bookings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    private UUID id;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "contact_email", nullable = false)
    private String contactEmail;

    @Column(name = "screening_id", nullable = false)
    private UUID screeningId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BookingStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(
            name = "total_price",
            nullable = false,
            precision = 10,
            scale = 2)
    private BigDecimal totalPrice;

    @OneToMany(mappedBy = "booking",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<BookingSeat> seats = new ArrayList<>();


    private Booking(
            UUID id,
            UUID customerId,
            String contactEmail,
            UUID screeningId,
            BookingStatus status,
            LocalDateTime createdAt,
            LocalDateTime expiresAt,
            BigDecimal totalPrice
    ) {
        this.id = id;
        this.customerId = customerId;
        this.contactEmail = contactEmail;
        this.screeningId = screeningId;
        this.status = status;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.totalPrice = totalPrice;
    }

    public static Booking create(
            UUID customerId,
            String contactEmail,
            UUID screeningId,
            LocalDateTime createdAt
    ) {
        return new Booking(
                UUID.randomUUID(),
                customerId,
                contactEmail,
                screeningId,
                BookingStatus.PENDING,
                createdAt,
                null,
                BigDecimal.ZERO);
    }

    public void addSeat(
            UUID seatId,
            BigDecimal price
    ) {
        boolean alreadyAdded = seats.stream()
                .anyMatch(seat -> seat.getSeatId()
                                .equals(seatId));

        if (alreadyAdded) {
            throw new SeatAlreadyAddedException(seatId);
        }

        BookingSeat bookingSeat = BookingSeat.create(this, seatId, price);

        seats.add(bookingSeat);

        totalPrice = totalPrice.add(price);
    }

    public void markAsHeld(LocalDateTime expiresAt) {
        this.status = BookingStatus.HELD;
        this.expiresAt = expiresAt;
    }

    public void cancel(LocalDateTime now, LocalDateTime screeningStartsAt
    ) {
        if (status != BookingStatus.HELD) {
            throw new BookingCancellationNotAllowedException(id);
        }

        if (!now.isBefore(screeningStartsAt)) {
            throw new BookingCancellationNotAllowedException(id);
        }

        status = BookingStatus.CANCELLED;
    }

    // confirm only a held booking whose booking deadline has not passed
    public void markAsConfirmed(LocalDateTime confirmedAt) {
        Objects.requireNonNull(confirmedAt, "Confirmation time is required");

        if (status != BookingStatus.HELD
                || expiresAt == null
                || !confirmedAt.isBefore(expiresAt)
                || seats.isEmpty()) {
            throw new IllegalStateException(
                    "Booking %s cannot be confirmed".formatted(id)
            );
        }

        status = BookingStatus.CONFIRMED;
    }

    public void markAsExpired() {
        if (status != BookingStatus.HELD) {
            throw new IllegalStateException("Only held booking can be marked as expired");
        }

        status = BookingStatus.EXPIRED;
    }
}