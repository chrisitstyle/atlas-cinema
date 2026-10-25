package com.atlascinema.screening.controller;

import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.service.ScreeningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScreeningControllerTest {

    @Mock
    private ScreeningService screeningService;

    @InjectMocks
    private ScreeningController screeningController;

    private UUID screeningId;
    private UUID movieId;
    private UUID hallId;

    private LocalDateTime startsAt;

    private CreateScreeningRequest createScreeningRequest;
    private ScreeningResponse screeningResponse;

    @BeforeEach
    void setUp() {
        screeningId = UUID.randomUUID();
        movieId = UUID.randomUUID();
        hallId = UUID.randomUUID();

        startsAt = LocalDateTime.of(2030, 1, 10, 18,0);

        createScreeningRequest = new CreateScreeningRequest(
                movieId,
                hallId,
                startsAt,
                new BigDecimal("29.90"));

        screeningResponse = createScreeningResponse(screeningId, ScreeningStatus.SCHEDULED);
    }

    @Test
    void createScreening_shouldReturnCreatedScreening_whenRequestIsValid() {
        when(screeningService.createScreening(createScreeningRequest))
                .thenReturn(screeningResponse);

        ResponseEntity<ScreeningResponse> response = screeningController.createScreening(createScreeningRequest);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/screenings/" + screeningId));

        assertThat(response.getBody())
                .isEqualTo(screeningResponse);

        verify(screeningService)
                .createScreening(createScreeningRequest);
    }

    @Test
    void getScreenings_shouldReturnAllScreenings_whenNoFilterIsProvided() {
        ScreeningResponse secondScreening = createScreeningResponse(UUID.randomUUID(),
                        ScreeningStatus.SCHEDULED);

        when(screeningService.getScreenings(null, null, null
        )).thenReturn(List.of(screeningResponse, secondScreening));

        List<ScreeningResponse> response = screeningController.getAllScreenings(null, null,null);

        assertThat(response)
                .hasSize(2)
                .containsExactly(screeningResponse,secondScreening);

        verify(screeningService)
                .getScreenings(null, null, null);
    }

    @Test
    void getScreenings_shouldReturnScreeningsByMovieId_whenMovieIdIsProvided() {
        when(screeningService.getScreenings(
                movieId,
                null,
                null
        )).thenReturn(
                List.of(screeningResponse)
        );

        List<ScreeningResponse> response = screeningController.getAllScreenings(movieId, null,null);

        assertThat(response)
                .hasSize(1)
                .first()
                .satisfies(screening ->
                        assertThat(screening.movieId())
                                .isEqualTo(movieId));

        verify(screeningService).getScreenings(
                        movieId,null,null);
    }

    @Test
    void getScreenings_shouldReturnScreeningsByHallId_whenHallIdIsProvided() {
        when(screeningService.getScreenings(
                null,
                hallId,
                null)).thenReturn(
                List.of(screeningResponse));

        List<ScreeningResponse> response = screeningController.getAllScreenings(
                        null,hallId,null);

        assertThat(response)
                .hasSize(1)
                .first()
                .satisfies(screening ->
                        assertThat(screening.hallId())
                                .isEqualTo(hallId));

        verify(screeningService)
                .getScreenings(null,hallId,null);
    }

    @Test
    void getScreenings_shouldReturnScreeningsByDate_whenDateIsProvided() {
        LocalDate date = startsAt.toLocalDate();

        when(screeningService.getScreenings(
                null,
                null,
                date
        )).thenReturn(
                List.of(screeningResponse)
        );

        List<ScreeningResponse> response = screeningController.getAllScreenings(null,null,
                        date);

        assertThat(response)
                .hasSize(1)
                .first()
                .satisfies(screening ->
                        assertThat(screening.startsAt().toLocalDate())
                                .isEqualTo(date));

        verify(screeningService)
                .getScreenings(null, null, date);
    }

    @Test
    void getScreenings_shouldReturnEmptyList_whenNoScreeningsExist() {
        when(screeningService.getScreenings(
                null,
                null,
                null
        )).thenReturn(List.of());

        List<ScreeningResponse> response = screeningController.getAllScreenings(
                        null,
                        null,
                        null);

        assertThat(response).isEmpty();

        verify(screeningService)
                .getScreenings(null,null, null);
    }

    @Test
    void getScreeningById_shouldReturnScreening_whenScreeningExists() {
        when(screeningService.getScreeningById(screeningId))
                .thenReturn(screeningResponse);

        ScreeningResponse response = screeningController.getScreeningById(screeningId);

        assertThat(response)
                .isEqualTo(screeningResponse);

        assertThat(response.id())
                .isEqualTo(screeningId);

        assertThat(response.status())
                .isEqualTo(ScreeningStatus.SCHEDULED);

        verify(screeningService)
                .getScreeningById(screeningId);
    }

    @Test
    void cancelScreening_shouldReturnCancelledScreening_whenScreeningIsScheduled() {
        ScreeningResponse cancelledScreening = createScreeningResponse(
                        screeningId,
                        ScreeningStatus.CANCELLED);

        when(screeningService.cancelScreening(screeningId))
                .thenReturn(cancelledScreening);

        ScreeningResponse response = screeningController.cancelScreening(screeningId);

        assertThat(response)
                .isEqualTo(cancelledScreening);

        assertThat(response.id())
                .isEqualTo(screeningId);

        assertThat(response.status())
                .isEqualTo(ScreeningStatus.CANCELLED);

        verify(screeningService)
                .cancelScreening(screeningId);
    }

    private ScreeningResponse createScreeningResponse(
            UUID id,
            ScreeningStatus status
    ) {
        return new ScreeningResponse(
                id,
                movieId,
                hallId,
                startsAt,
                startsAt.plusMinutes(120),
                new BigDecimal("29.90"),
                status
        );
    }
}
