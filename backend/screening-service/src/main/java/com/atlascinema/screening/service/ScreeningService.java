package com.atlascinema.screening.service;

import com.atlascinema.screening.client.CinemaClient;
import com.atlascinema.screening.client.MovieClient;
import com.atlascinema.screening.client.dto.MovieClientResponse;
import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.exception.*;
import com.atlascinema.screening.repository.ScreeningRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class ScreeningService {

    private static final long TECHNICAL_BREAK_MINUTES = 20;

    private final ScreeningRepository screeningRepository;
    private final MovieClient movieClient;
    private final CinemaClient cinemaClient;

    @Transactional
    public ScreeningResponse createScreening(
            CreateScreeningRequest request
    ) {
        MovieClientResponse movie = getMovie(request.movieId());

        if (movie.discontinued()) {
            throw new MovieUnavailableException(request.movieId());
        }

        ensureHallExists(request.hallId());

        Screening screening = Screening.create(
                request.movieId(),
                request.hallId(),
                request.startsAt(),
                movie.durationMinutes(),
                request.basePrice()
        );

        LocalDateTime windowStart = screening.getStartsAt()
                .minusMinutes(TECHNICAL_BREAK_MINUTES);

        LocalDateTime windowEnd = screening.getEndsAt()
                .plusMinutes(TECHNICAL_BREAK_MINUTES);

        long conflicts = screeningRepository.countConflictingScreenings(
                request.hallId(),
                ScreeningStatus.SCHEDULED,
                windowStart,
                windowEnd
        );

        if (conflicts > 0) {
            throw new ScreeningConflictException(
                    request.hallId(),
                    screening.getStartsAt(),
                    screening.getEndsAt()
            );
        }

        Screening saved = screeningRepository.save(screening);

        return ScreeningResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public ScreeningResponse getScreeningById(UUID screeningId) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new ScreeningNotFoundException(screeningId));

        return ScreeningResponse.from(screening);
    }

    @Transactional(readOnly = true)
    public List<ScreeningResponse> getScreenings(
            UUID movieId,
            UUID hallId,
            LocalDate date
    ) {
        validateFilters(movieId, hallId, date);

        if (movieId != null) {
            return toResponse(screeningRepository
                            .findAllByMovieIdOrderByStartsAtAsc(movieId));
        }

        if (hallId != null) {
            return toResponse(screeningRepository
                            .findAllByHallIdOrderByStartsAtAsc(hallId));
        }

        if (date != null) {
            LocalDateTime from = date.atStartOfDay();
            LocalDateTime to = date.plusDays(1).atStartOfDay();

            return toResponse(screeningRepository
                            .findAllByStartsAtGreaterThanEqualAndStartsAtLessThanOrderByStartsAtAsc(
                                    from, to));
        }

        return toResponse(screeningRepository.findAllByOrderByStartsAtAsc());
    }

    @Transactional
    public ScreeningResponse cancelScreening(UUID screeningId) {
        Screening screening = screeningRepository.findById(screeningId)
                .orElseThrow(() -> new ScreeningNotFoundException(screeningId));

        screening.cancel();

        return ScreeningResponse.from(screening);
    }

    private MovieClientResponse getMovie(UUID movieId) {
        try {
            return movieClient.getMovie(movieId);
        } catch (FeignException.NotFound exception) {
            throw new MovieNotFoundException(movieId);
        }
    }

    private void ensureHallExists(UUID hallId) {
        try {
            cinemaClient.getHall(hallId);
        } catch (FeignException.NotFound exception) {
            throw new HallNotFoundException(hallId);
        }
    }

    private void validateFilters(
            UUID movieId,
            UUID hallId,
            LocalDate date
    ) {
        long filtersCount = Stream.of(movieId, hallId, date)
                .filter(Objects::nonNull)
                .count();

        if (filtersCount > 1) {
            throw new InvalidScreeningFilterException();
        }
    }

    private List<ScreeningResponse> toResponse(
            List<Screening> screenings
    ) {
        return screenings.stream()
                .map(ScreeningResponse::from)
                .toList();
    }
}