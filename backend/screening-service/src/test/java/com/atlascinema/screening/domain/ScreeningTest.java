package com.atlascinema.screening.domain;

import com.atlascinema.screening.exception.ScreeningAlreadyCancelledException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScreeningTest {

    @Test
    void create_shouldCreateScheduledScreening_whenDataIsValid() {
        UUID movieId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();

        LocalDateTime startsAt =
                LocalDateTime.of(2030, 1, 10, 18, 0);

        Screening screening = Screening.create(
                movieId,
                hallId,
                startsAt,
                120,
                new BigDecimal("29.90"));

        assertThat(screening.getId()).isNotNull();
        assertThat(screening.getMovieId()).isEqualTo(movieId);
        assertThat(screening.getHallId()).isEqualTo(hallId);
        assertThat(screening.getStartsAt()).isEqualTo(startsAt);
        assertThat(screening.getEndsAt())
                .isEqualTo(startsAt.plusMinutes(120));
        assertThat(screening.getBasePrice())
                .isEqualByComparingTo("29.90");
        assertThat(screening.getStatus())
                .isEqualTo(ScreeningStatus.SCHEDULED);
    }

    @Test
    void cancel_shouldSetStatusToCancelled_whenScreeningIsScheduled() {
        Screening screening = createScreening();

        screening.cancel();

        assertThat(screening.getStatus())
                .isEqualTo(ScreeningStatus.CANCELLED);
    }

    @Test
    void cancel_shouldThrowScreeningAlreadyCancelledException_whenScreeningIsAlreadyCancelled() {
        Screening screening = createScreening();
        screening.cancel();

        assertThatThrownBy(screening::cancel)
                .isInstanceOf(ScreeningAlreadyCancelledException.class)
                .hasMessageContaining(screening.getId().toString());
    }

    private Screening createScreening() {
        return Screening.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 18, 0),
                120,
                new BigDecimal("29.90"));
    }
}
