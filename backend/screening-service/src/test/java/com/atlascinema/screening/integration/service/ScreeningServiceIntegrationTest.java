package com.atlascinema.screening.integration.service;

import com.atlascinema.screening.client.CinemaClient;
import com.atlascinema.screening.client.MovieClient;
import com.atlascinema.screening.client.dto.HallClientResponse;
import com.atlascinema.screening.client.dto.MovieClientResponse;
import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.exception.ScreeningConflictException;
import com.atlascinema.screening.integration.AbstractIntegrationTest;
import com.atlascinema.screening.repository.ScreeningRepository;
import com.atlascinema.screening.service.ScreeningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

class ScreeningServiceIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private ScreeningService screeningService;

    @Autowired
    private ScreeningRepository screeningRepository;

    @MockitoBean
    private MovieClient movieClient;

    @MockitoBean
    private CinemaClient cinemaClient;

    @Test
    void createScreening_shouldPersistScreening_whenRequestIsValid() {
        UUID movieId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();

        LocalDateTime startsAt = LocalDateTime.now()
                        .plusDays(10)
                        .withNano(0);

        when(movieClient.getMovie(movieId))
                .thenReturn(new MovieClientResponse(
                        movieId,
                        120,
                        false));

        when(cinemaClient.getHall(hallId))
                .thenReturn(new HallClientResponse(
                        hallId,
                        "Sala 1",
                        UUID.randomUUID()));

        CreateScreeningRequest request = new CreateScreeningRequest(
                        movieId,
                        hallId,
                        startsAt,
                        new BigDecimal("29.90"));

        ScreeningResponse response = screeningService.createScreening(request);

        Screening saved = screeningRepository
                .findById(response.id())
                .orElseThrow();

        assertThat(saved.getMovieId()).isEqualTo(movieId);
        assertThat(saved.getHallId()).isEqualTo(hallId);
        assertThat(saved.getStartsAt()).isEqualTo(startsAt);
        assertThat(saved.getEndsAt())
                .isEqualTo(startsAt.plusMinutes(120));
        assertThat(saved.getStatus())
                .isEqualTo(ScreeningStatus.SCHEDULED);
    }

    @Test
    void createScreening_shouldThrowScreeningConflictException_whenScreeningOverlapsExistingScreening() {
        UUID movieId = UUID.randomUUID();
        UUID hallId = UUID.randomUUID();

        LocalDateTime startsAt = LocalDateTime.now()
                        .plusDays(10)
                        .withNano(0);

        Screening existing = Screening.create(
                UUID.randomUUID(),
                hallId,
                startsAt,
                120,
                new BigDecimal("29.90"));

        screeningRepository.saveAndFlush(existing);

        when(movieClient.getMovie(movieId))
                .thenReturn(new MovieClientResponse(
                        movieId,
                        120,
                        false));

        when(cinemaClient.getHall(hallId))
                .thenReturn(new HallClientResponse(
                        hallId,
                        "Sala 1",
                        UUID.randomUUID()));

        CreateScreeningRequest request = new CreateScreeningRequest(
                        movieId,
                        hallId,
                        startsAt.plusMinutes(30),
                        new BigDecimal("29.90"));

        assertThatThrownBy(() -> screeningService.createScreening(request))
                .isInstanceOf(ScreeningConflictException.class);

        assertThat(screeningRepository.count()).isEqualTo(1);
    }

    @Test
    void cancelScreening_shouldPersistCancelledStatus_whenScreeningIsScheduled() {
        Screening screening = screeningRepository.saveAndFlush(createScreening());

        ScreeningResponse response = screeningService.cancelScreening(screening.getId());

        assertThat(response.status())
                .isEqualTo(ScreeningStatus.CANCELLED);

        Screening updated = screeningRepository
                .findById(screening.getId())
                .orElseThrow();

        assertThat(updated.getStatus())
                .isEqualTo(ScreeningStatus.CANCELLED);
    }

    @Test
    void getScreeningById_shouldReturnScreening_whenScreeningExists() {
        Screening screening = screeningRepository.saveAndFlush(createScreening());

        ScreeningResponse response = screeningService.getScreeningById(screening.getId());

        assertThat(response.id()).isEqualTo(screening.getId());
    }

    private Screening createScreening() {
        return Screening.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                LocalDateTime.now()
                        .plusDays(10)
                        .withNano(0),
                120,
                new BigDecimal("29.90"));
    }
}
