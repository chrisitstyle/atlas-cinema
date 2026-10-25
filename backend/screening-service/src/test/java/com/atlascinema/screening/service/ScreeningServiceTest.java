package com.atlascinema.screening.service;

import com.atlascinema.screening.client.CinemaClient;
import com.atlascinema.screening.client.MovieClient;
import com.atlascinema.screening.client.dto.HallClientResponse;
import com.atlascinema.screening.client.dto.MovieClientResponse;
import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.exception.*;
import com.atlascinema.screening.repository.ScreeningRepository;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScreeningServiceTest {

    @Mock
    private ScreeningRepository screeningRepository;

    @Mock
    private MovieClient movieClient;

    @Mock
    private CinemaClient cinemaClient;

    @InjectMocks
    private ScreeningService screeningService;

    private UUID movieId;
    private UUID hallId;
    private LocalDateTime startsAt;
    private CreateScreeningRequest request;

    @BeforeEach
    void setUp() {
        movieId = UUID.randomUUID();
        hallId = UUID.randomUUID();

        startsAt = LocalDateTime.of(2030, 1, 10, 18, 0);

        request = new CreateScreeningRequest(
                movieId,
                hallId,
                startsAt,
                new BigDecimal("29.90"));
    }

    @Test
    void createScreening_shouldCreateScreening_whenRequestIsValid() {
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

        when(screeningRepository.countConflictingScreenings(
                hallId,
                ScreeningStatus.SCHEDULED,
                startsAt.minusMinutes(20),
                startsAt.plusMinutes(140)
        )).thenReturn(0L);

        when(screeningRepository.save(any(Screening.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ScreeningResponse response = screeningService.createScreening(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.movieId()).isEqualTo(movieId);
        assertThat(response.hallId()).isEqualTo(hallId);
        assertThat(response.startsAt()).isEqualTo(startsAt);
        assertThat(response.endsAt())
                .isEqualTo(startsAt.plusMinutes(120));
        assertThat(response.status())
                .isEqualTo(ScreeningStatus.SCHEDULED);

        verify(movieClient).getMovie(movieId);
        verify(cinemaClient).getHall(hallId);
        verify(screeningRepository).save(any(Screening.class));
    }

    @Test
    void createScreening_shouldThrowMovieUnavailableException_whenMovieIsDiscontinued() {
        when(movieClient.getMovie(movieId))
                .thenReturn(new MovieClientResponse(
                        movieId,
                        120,
                        true));

        assertThatThrownBy(() -> screeningService.createScreening(request)
        )
                .isInstanceOf(MovieUnavailableException.class)
                .hasMessageContaining(movieId.toString());

        verifyNoInteractions(cinemaClient);
        verifyNoInteractions(screeningRepository);
    }

    @Test
    void createScreening_shouldThrowMovieNotFoundException_whenMovieDoesNotExist() {
        FeignException.NotFound exception = mock(FeignException.NotFound.class);

        when(movieClient.getMovie(movieId))
                .thenThrow(exception);

        assertThatThrownBy(
                () -> screeningService.createScreening(request)
        )
                .isInstanceOf(MovieNotFoundException.class)
                .hasMessageContaining(movieId.toString());

        verifyNoInteractions(cinemaClient);
        verifyNoInteractions(screeningRepository);
    }

    @Test
    void createScreening_shouldThrowHallNotFoundException_whenHallDoesNotExist() {
        when(movieClient.getMovie(movieId))
                .thenReturn(new MovieClientResponse(
                        movieId,
                        120,
                        false));

        FeignException.NotFound exception = mock(FeignException.NotFound.class);

        when(cinemaClient.getHall(hallId))
                .thenThrow(exception);

        assertThatThrownBy(
                () -> screeningService.createScreening(request)
        )
                .isInstanceOf(HallNotFoundException.class)
                .hasMessageContaining(hallId.toString());

        verifyNoInteractions(screeningRepository);
    }

    @Test
    void createScreening_shouldThrowScreeningConflictException_whenScreeningConflicts() {
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

        when(screeningRepository.countConflictingScreenings(
                hallId,
                ScreeningStatus.SCHEDULED,
                startsAt.minusMinutes(20),
                startsAt.plusMinutes(140))).thenReturn(1L);

        assertThatThrownBy(() -> screeningService.createScreening(request)
        ).isInstanceOf(ScreeningConflictException.class);

        verify(screeningRepository, never())
                .save(any(Screening.class));
    }

    @Test
    void getScreenings_shouldReturnScreeningsByMovie_whenMovieIdIsProvided() {
        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        when(screeningRepository
                .findAllByMovieIdOrderByStartsAtAsc(movieId))
                .thenReturn(List.of(screening));

        List<ScreeningResponse> response = screeningService.getScreenings(
                        movieId,
                        null,
                        null);

        assertThat(response)
                .hasSize(1)
                .first()
                .satisfies(result ->
                        assertThat(result.movieId())
                                .isEqualTo(movieId));

        verify(screeningRepository)
                .findAllByMovieIdOrderByStartsAtAsc(movieId);
    }

    @Test
    void getScreenings_shouldReturnScreeningsByHall_whenHallIdIsProvided() {
        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        when(screeningRepository
                .findAllByHallIdOrderByStartsAtAsc(hallId))
                .thenReturn(List.of(screening));

        List<ScreeningResponse> response = screeningService.getScreenings(
                        null,
                        hallId,
                        null);

        assertThat(response)
                .hasSize(1)
                .first()
                .satisfies(result ->
                        assertThat(result.hallId())
                                .isEqualTo(hallId));

        verify(screeningRepository)
                .findAllByHallIdOrderByStartsAtAsc(hallId);
    }

    @Test
    void getScreenings_shouldReturnScreeningsByDate_whenDateIsProvided() {
        LocalDate date = startsAt.toLocalDate();

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();

        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        when(screeningRepository
                .findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        from,
                        to
                ))
                .thenReturn(List.of(screening));

        List<ScreeningResponse> response = screeningService.getScreenings(
                        null,
                        null,
                        date);

        assertThat(response).hasSize(1);

        verify(screeningRepository)
                .findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                        from,
                        to);
    }

    @Test
    void getScreenings_shouldReturnAllScreenings_whenNoFilterIsProvided() {
        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        when(screeningRepository.findAllByOrderByStartsAtAsc())
                .thenReturn(List.of(screening));

        List<ScreeningResponse> response = screeningService.getScreenings(
                        null,
                        null,
                        null);

        assertThat(response).hasSize(1);

        verify(screeningRepository)
                .findAllByOrderByStartsAtAsc();
    }

    @Test
    void getScreenings_shouldThrowInvalidScreeningFilterException_whenMultipleFiltersAreProvided() {
        assertThatThrownBy(() ->
                screeningService.getScreenings(
                        movieId,
                        hallId,
                        null))
                .isInstanceOf(InvalidScreeningFilterException.class);

        verifyNoInteractions(screeningRepository);
    }

    @Test
    void getScreeningById_shouldReturnScreening_whenScreeningExists() {
        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        when(screeningRepository.findById(screening.getId()))
                .thenReturn(Optional.of(screening));

        ScreeningResponse response = screeningService.getScreeningById(
                        screening.getId());

        assertThat(response.id())
                .isEqualTo(screening.getId());
    }

    @Test
    void getScreeningById_shouldThrowScreeningNotFoundException_whenScreeningDoesNotExist() {
        UUID screeningId = UUID.randomUUID();

        when(screeningRepository.findById(screeningId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                screeningService.getScreeningById(screeningId))
                .isInstanceOf(ScreeningNotFoundException.class)
                .hasMessageContaining(screeningId.toString());
    }

    @Test
    void cancelScreening_shouldCancelScreening_whenScreeningIsScheduled() {
        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        when(screeningRepository.findById(screening.getId()))
                .thenReturn(Optional.of(screening));

        ScreeningResponse response = screeningService.cancelScreening(
                        screening.getId());

        assertThat(response.status())
                .isEqualTo(ScreeningStatus.CANCELLED);

        verify(screeningRepository, never())
                .save(any(Screening.class));
    }

    @Test
    void cancelScreening_shouldThrowScreeningAlreadyCancelledException_whenScreeningIsAlreadyCancelled() {
        Screening screening = createScreening(
                movieId,
                hallId,
                startsAt);

        screening.cancel();

        when(screeningRepository.findById(screening.getId()))
                .thenReturn(Optional.of(screening));

        assertThatThrownBy(() -> screeningService.cancelScreening(screening.getId()))
                .isInstanceOf(ScreeningAlreadyCancelledException.class);
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
