package com.atlascinema.screening.integration.repository;

import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.integration.BaseTestEnvironment;
import com.atlascinema.screening.repository.ScreeningRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE)
class ScreeningRepositoryIntegrationTest
        extends BaseTestEnvironment {

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void findById_shouldReturnScreening_whenScreeningExists() {
        Screening screening = screeningRepository.saveAndFlush(
                createScreening(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        LocalDateTime.of(2030, 1, 10, 18, 0)));

        entityManager.clear();

        Screening found = screeningRepository
                .findById(screening.getId())
                .orElseThrow();

        assertThat(found.getId())
                .isEqualTo(screening.getId());

        assertThat(found.getStatus())
                .isEqualTo(ScreeningStatus.SCHEDULED);
    }

    @Test
    void findAllByMovieIdOrderByStartsAtAsc_shouldReturnSortedScreenings_whenMovieMatches() {
        UUID movieId = UUID.randomUUID();

        Screening later = createScreening(
                movieId,
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 20, 0));

        Screening earlier = createScreening(
                movieId,
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 16, 0));

        screeningRepository.saveAllAndFlush(List.of(later, earlier));

        entityManager.clear();

        List<Screening> screenings = screeningRepository
                        .findAllByMovieIdOrderByStartsAtAsc(movieId);

        assertThat(screenings)
                .extracting(Screening::getStartsAt)
                .containsExactly(
                        earlier.getStartsAt(),
                        later.getStartsAt());
    }

    @Test
    void findAllByHallIdOrderByStartsAtAsc_shouldReturnOnlyHallScreenings_whenHallMatches() {
        UUID hallId = UUID.randomUUID();

        Screening first = createScreening(
                UUID.randomUUID(),
                hallId,
                LocalDateTime.of(2030, 1, 10, 16, 0)
        );

        Screening second = createScreening(UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.of(2030, 1, 10, 18, 0));

        screeningRepository.saveAllAndFlush(List.of(first, second));

        List<Screening> result = screeningRepository
                        .findAllByHallIdOrderByStartsAtAsc(hallId);

        assertThat(result)
                .hasSize(1)
                .first()
                .satisfies(screening ->
                        assertThat(screening.getHallId())
                                .isEqualTo(hallId));
    }

    @Test
    void findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc_shouldReturnScreeningsFromDate_whenDateMatches() {
        screeningRepository.saveAllAndFlush(List.of(
                createScreening(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        LocalDateTime.of(2030, 1, 10, 18, 0)),
                createScreening(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        LocalDateTime.of(2030, 1, 11, 18, 0))));

        LocalDateTime from = LocalDateTime.of(2030, 1, 10, 0, 0);

        LocalDateTime to = LocalDateTime.of(2030, 1, 11, 0, 0);

        List<Screening> result = screeningRepository
                        .findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                                from,
                                to);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getStartsAt().toLocalDate())
                .isEqualTo(from.toLocalDate());
    }

    @Test
    void countConflictingScreenings_shouldReturnConflict_whenTechnicalBreakIsViolated() {
        UUID hallId = UUID.randomUUID();

        Screening existing = createScreening(
                UUID.randomUUID(),
                hallId,
                LocalDateTime.of(2030, 1, 10, 18, 0)
        );

        screeningRepository.saveAndFlush(existing);

        long conflicts =
                screeningRepository.countConflictingScreenings(
                        hallId,
                        ScreeningStatus.SCHEDULED,
                        LocalDateTime.of(2030, 1, 10, 19, 50),
                        LocalDateTime.of(2030, 1, 10, 22, 30));

        assertThat(conflicts).isEqualTo(1);
    }

    @Test
    void countConflictingScreenings_shouldReturnNoConflict_whenTechnicalBreakIsExactlyTwentyMinutes() {
        UUID hallId = UUID.randomUUID();

        Screening existing = createScreening(
                UUID.randomUUID(),
                hallId,
                LocalDateTime.of(2030, 1, 10, 18, 0));

        screeningRepository.saveAndFlush(existing);

        long conflicts =
                screeningRepository.countConflictingScreenings(
                        hallId,
                        ScreeningStatus.SCHEDULED,
                        LocalDateTime.of(2030, 1, 10, 20, 0),
                        LocalDateTime.of(
                                2030, 1, 10, 22, 40));

        assertThat(conflicts).isZero();
    }

    private Screening createScreening(
            UUID movieId,
            UUID hallId,
            LocalDateTime startsAt
    ) {
        return Screening.create(
                movieId,
                hallId,
                startsAt,
                120,
                new BigDecimal("29.90"));
    }
}
