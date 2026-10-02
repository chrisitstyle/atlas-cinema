package com.atlascinema.cinema.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(
        name = "seats",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_seat_hall_row_number",
                        columnNames = {"hall_id", "seat_row", "seat_number"}
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Seat {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hall_id", nullable = false)
    private Hall hall;

    @Column(name = "seat_row", nullable = false, length = 10)
    private String row;

    @Column(name = "seat_number", nullable = false)
    private Integer number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SeatType type;

    private Seat(
            UUID id,
            Hall hall,
            String row,
            Integer number,
            SeatType type
    ) {
        this.id = id;
        this.hall = hall;
        this.row = row;
        this.number = number;
        this.type = type;
    }

    static Seat create(
            Hall hall,
            String row,
            Integer number,
            SeatType type
    ) {
        return new Seat(
                UUID.randomUUID(),
                hall,
                row,
                number,
                type
        );
    }
}