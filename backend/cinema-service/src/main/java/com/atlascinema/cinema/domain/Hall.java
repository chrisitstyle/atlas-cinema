package com.atlascinema.cinema.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "halls")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Hall {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cinema_id", nullable = false)
    private Cinema cinema;

    @OneToMany(
            mappedBy = "hall",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Seat> seats = new ArrayList<>();

    private Hall(
            UUID id,
            String name,
            Cinema cinema
    ) {
        this.id = id;
        this.name = name;
        this.cinema = cinema;
    }

    static Hall create(
            String name,
            Cinema cinema
    ) {
        return new Hall(
                UUID.randomUUID(),
                name,
                cinema
        );
    }


    public Seat addSeat(
            String row,
            Integer number,
            SeatType type
    ) {
        Seat seat = Seat.create(
                this,
                row,
                number,
                type
        );

        seats.add(seat);

        return seat;
    }
}
