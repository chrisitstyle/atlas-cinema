package com.atlascinema.movie.integration.controller;

import com.atlascinema.movie.domain.AgeRating;
import com.atlascinema.movie.domain.Genre;
import com.atlascinema.movie.domain.Movie;
import com.atlascinema.movie.dto.CreateMovieRequest;
import com.atlascinema.movie.dto.UpdateMovieRequest;
import com.atlascinema.movie.integration.AbstractIntegrationTest;
import com.atlascinema.movie.repository.MovieRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class MovieControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MovieRepository movieRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void createMovie_shouldCreateMovie_whenRequestIsValid() throws Exception {
        CreateMovieRequest request = new CreateMovieRequest(
                "Interstellar",
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                "https://test.com/poster.jpg",
                "https://test.com/trailer");

        MvcResult result = mockMvc.perform(post("/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Interstellar"))
                .andExpect(jsonPath("$.durationMinutes").value(169))
                .andExpect(jsonPath("$.discontinued").value(false))
                .andReturn();

        String location = result.getResponse().getHeader("Location");

        assertThat(location).startsWith("/movies/");

        UUID id = UUID.fromString(
                location.substring("/movies/".length())
        );

        Movie saved = movieRepository.findById(id).orElseThrow();

        assertThat(saved.getTitle()).isEqualTo("Interstellar");
    }

    @Test
    void getMovieById_shouldReturnMovie_whenMovieExists() throws Exception {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        mockMvc.perform(get("/movies/{id}", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(movie.getId().toString()))
                .andExpect(jsonPath("$.title").value("Interstellar"));
    }

    @Test
    void getAllMovies_shouldReturnAllMovies_whenMoviesExist() throws Exception {
        movieRepository.saveAllAndFlush(List.of(
                createMovie("Interstellar"),
                createMovie("Dune")
        ));

        mockMvc.perform(get("/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].title")
                        .value(containsInAnyOrder("Interstellar", "Dune")));
    }

    @Test
    void updateMovie_shouldUpdateMovieDetails_whenMovieExists() throws Exception {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        UpdateMovieRequest request = new UpdateMovieRequest(
                "Updated movie",
                "Updated description",
                180,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null);

        mockMvc.perform(put("/movies/{id}", movie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated movie"))
                .andExpect(jsonPath("$.durationMinutes").value(180));

        Movie updated = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(updated.getTitle()).isEqualTo("Updated movie");
        assertThat(updated.getDurationMinutes()).isEqualTo(180);
    }

    @Test
    void updateMovie_shouldReturn400_whenRequestIsInvalid() throws Exception {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        UpdateMovieRequest request = new UpdateMovieRequest(
                "",
                "Invalid description",
                -10,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        mockMvc.perform(put("/movies/{id}", movie.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        Movie unchanged = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(unchanged.getTitle()).isEqualTo("Interstellar");
        assertThat(unchanged.getDurationMinutes()).isEqualTo(169);
        assertThat(unchanged.getGenre()).isEqualTo(Genre.SCIENCE_FICTION);

        assertThat(movieRepository.count()).isEqualTo(1);
    }

    @Test
    void discontinueMovie_shouldDiscontinueMovie_whenMovieIsActive() throws Exception {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        mockMvc.perform(patch("/movies/{id}/discontinue", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discontinued").value(true));

        Movie updated = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(updated.isDiscontinued()).isTrue();
    }

    @Test
    void restoreMovie_shouldRestoreMovie_whenMovieIsDiscontinued() throws Exception {
        Movie movie = createMovie("Interstellar");
        movie.discontinue();
        movieRepository.saveAndFlush(movie);

        mockMvc.perform(patch("/movies/{id}/restore", movie.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.discontinued").value(false));

        Movie restored = movieRepository.findById(movie.getId())
                .orElseThrow();

        assertThat(restored.isDiscontinued()).isFalse();
    }

    @Test
    void getMovie_shouldReturn404_whenMovieDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/movies/{id}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Movie not found"));
    }

    @Test
    void discontinueMovie_shouldReturn409_whenMovieAlreadyDiscontinued() throws Exception {
        Movie movie = createMovie("Interstellar");
        movie.discontinue();
        movieRepository.saveAndFlush(movie);

        mockMvc.perform(patch("/movies/{id}/discontinue", movie.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Movie already discontinued"));
    }

    @Test
    void restoreMovie_shouldReturn409_whenMovieAlreadyActive() throws Exception {
        Movie movie = movieRepository.saveAndFlush(
                createMovie("Interstellar")
        );

        mockMvc.perform(patch("/movies/{id}/restore", movie.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Movie already active"));
    }

    @Test
    void createMovie_shouldReturn400_whenRequestIsInvalid() throws Exception {
        CreateMovieRequest request = new CreateMovieRequest(
                "",
                "Invalid movie",
                -10,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        mockMvc.perform(post("/movies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(movieRepository.count()).isZero();
    }

    @Test
    void updateMovie_shouldReturn404_whenMovieDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        UpdateMovieRequest request = new UpdateMovieRequest(
                "Updated movie",
                "Updated description",
                180,
                AgeRating.R,
                Genre.DRAMA,
                LocalDate.of(2026, 1, 1),
                null,
                null
        );

        mockMvc.perform(put("/movies/{id}", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Movie not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(unknownId.toString())));

        assertThat(movieRepository.count()).isZero();
    }

    @Test
    void discontinueMovie_shouldReturn404_whenMovieDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(patch("/movies/{id}/discontinue", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Movie not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(unknownId.toString())));
    }

    @Test
    void restoreMovie_shouldReturn404_whenMovieDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(patch("/movies/{id}/restore", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Movie not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(unknownId.toString())));
    }


    private Movie createMovie(String title) {
        return Movie.create(
                title,
                "Sci-fi movie",
                169,
                AgeRating.PG_13,
                Genre.SCIENCE_FICTION,
                LocalDate.of(2026, 11, 7),
                null,
                null
        );
    }
}