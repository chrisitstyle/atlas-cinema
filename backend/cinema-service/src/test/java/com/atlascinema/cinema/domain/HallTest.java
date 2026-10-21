package com.atlascinema.cinema.domain;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HallTest {

    private Hall hall;

    @BeforeEach
    void setUp() {
        Cinema cinema = Cinema.create(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10");

        hall = cinema.addHall("Sala 1");
    }

    @Test
    void addSeat_shouldCreateSeat_whenDataIsValid() {
        Seat seat = hall.addSeat("A", 1, SeatType.STANDARD);

        assertThat(seat.getId()).isNotNull();
        assertThat(seat.getRow()).isEqualTo("A");
        assertThat(seat.getNumber()).isEqualTo(1);
        assertThat(seat.getType()).isEqualTo(SeatType.STANDARD);
        assertThat(seat.getHall()).isSameAs(hall);

        assertThat(hall.getSeats())
                .hasSize(1)
                .containsExactly(seat);
    }

    @Test
    void addSeat_shouldAddMultipleSeats_whenCoordinatesAreDifferent() {
        Seat firstSeat = hall.addSeat("A", 1, SeatType.STANDARD);
        Seat secondSeat = hall.addSeat("A", 2, SeatType.PREMIUM);
        Seat thirdSeat = hall.addSeat("B", 1, SeatType.VIP);

        assertThat(hall.getSeats())
                .hasSize(3)
                .containsExactly(firstSeat, secondSeat, thirdSeat);

        assertThat(firstSeat.getId())
                .isNotEqualTo(secondSeat.getId());

        assertThat(secondSeat.getId())
                .isNotEqualTo(thirdSeat.getId());

        assertThat(hall.getSeats())
                .extracting(Seat::getType)
                .containsExactly(
                        SeatType.STANDARD,
                        SeatType.PREMIUM,
                        SeatType.VIP
                );
    }

    @Test
    void addSeat_shouldCreateWheelchairSeat_whenTypeIsWheelchair() {
        Seat seat = hall.addSeat("C", 1, SeatType.WHEELCHAIR);

        assertThat(seat.getType()).isEqualTo(SeatType.WHEELCHAIR);
        assertThat(seat.getRow()).isEqualTo("C");
        assertThat(seat.getNumber()).isEqualTo(1);
        assertThat(seat.getHall()).isSameAs(hall);
    }
}
