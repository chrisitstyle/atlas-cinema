package com.atlascinema.movie.service;

import com.atlascinema.movie.domain.Movie;
import com.atlascinema.movie.dto.CreateMovieRequest;
import com.atlascinema.movie.dto.MovieResponse;
import com.atlascinema.movie.dto.UpdateMovieRequest;
import com.atlascinema.movie.exception.MovieNotFoundException;
import com.atlascinema.movie.repository.MovieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieRepository movieRepository;

    @Transactional
    public MovieResponse createMovie(CreateMovieRequest request) {

        Movie movie = Movie.create(
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.ageRating(),
                request.genre(),
                request.releaseDate(),
                request.posterUrl(),
                request.trailerUrl());

        Movie savedMovie = movieRepository.save(movie);
        return MovieResponse.from(savedMovie);
    }


    @Transactional(readOnly = true)
    public MovieResponse getMovieById(UUID id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));

        return MovieResponse.from(movie);
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> getAllMovies() {

        return movieRepository.findAll()
                .stream()
                .map(MovieResponse::from)
                .toList();
    }

    @Transactional
    public MovieResponse updateMovie(
            UUID id,
            UpdateMovieRequest request
    ) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));

        movie.updateDetails(
                request.title(),
                request.description(),
                request.durationMinutes(),
                request.ageRating(),
                request.genre(),
                request.releaseDate(),
                request.posterUrl(),
                request.trailerUrl()
        );

        return MovieResponse.from(movie);
    }

    @Transactional
    public MovieResponse discontinueMovie(UUID id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));

        movie.discontinue();

        return MovieResponse.from(movie);
    }

    @Transactional
    public MovieResponse restoreMovie(UUID id) {
        Movie movie = movieRepository.findById(id)
                .orElseThrow(() -> new MovieNotFoundException(id));

        movie.restore();

        return MovieResponse.from(movie);
    }
}
