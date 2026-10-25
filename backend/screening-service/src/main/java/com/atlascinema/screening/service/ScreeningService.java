package com.atlascinema.screening.service;

import com.atlascinema.screening.client.CinemaClient;
import com.atlascinema.screening.client.MovieClient;
import com.atlascinema.screening.client.dto.MovieClientResponse;
import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.dto.ScreeningResponse;
import com.atlascinema.screening.exception.HallNotFoundException;
import com.atlascinema.screening.exception.MovieNotFoundException;
import com.atlascinema.screening.exception.MovieUnavailableException;
import com.atlascinema.screening.exception.ScreeningConflictException;
import com.atlascinema.screening.repository.ScreeningRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

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
            throw new MovieUnavailableException(
                    request.movieId());
        }

        ensureHallExists(request.hallId());

        Screening screening = Screening.create(
                request.movieId(),
                request.hallId(),
                request.startsAt(),
                movie.durationMinutes(),
                request.basePrice());

        LocalDateTime windowStart = screening.getStartsAt()
                        .minusMinutes(TECHNICAL_BREAK_MINUTES);

        LocalDateTime windowEnd = screening.getEndsAt()
                        .plusMinutes(TECHNICAL_BREAK_MINUTES);

        long conflicts = screeningRepository.countConflictingScreenings(
                        request.hallId(),
                        ScreeningStatus.SCHEDULED,
                        windowStart,
                        windowEnd);

        if (conflicts > 0) {
            throw new ScreeningConflictException(
                    request.hallId(),
                    screening.getStartsAt(),
                    screening.getEndsAt());
        }

        Screening saved =
                screeningRepository.save(screening);

        return ScreeningResponse.from(saved);
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
}
