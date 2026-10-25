package com.atlascinema.screening.integration.controller;

import com.atlascinema.screening.client.CinemaClient;
import com.atlascinema.screening.client.MovieClient;
import com.atlascinema.screening.client.dto.HallClientResponse;
import com.atlascinema.screening.client.dto.MovieClientResponse;
import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import com.atlascinema.screening.dto.CreateScreeningRequest;
import com.atlascinema.screening.integration.AbstractIntegrationTest;
import com.atlascinema.screening.repository.ScreeningRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ScreeningControllerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ScreeningRepository screeningRepository;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private MovieClient movieClient;

    @MockitoBean
    private CinemaClient cinemaClient;

    @Test
    void createScreening_shouldCreateScreening_whenRequestIsValid()
            throws Exception {

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

        mockMvc.perform(post("/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.movieId")
                        .value(movieId.toString()))
                .andExpect(jsonPath("$.hallId")
                        .value(hallId.toString()))
                .andExpect(jsonPath("$.status")
                        .value("SCHEDULED"));

        assertThat(screeningRepository.count())
                .isEqualTo(1);
    }

    @Test
    void getScreeningById_shouldReturnScreening_whenScreeningExists() throws Exception {

        Screening screening = screeningRepository.saveAndFlush(createScreening());

        mockMvc.perform(get("/screenings/{screeningId}", screening.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(screening.getId().toString()))
                .andExpect(jsonPath("$.status")
                        .value("SCHEDULED"));
    }

    @Test
    void getScreeningById_shouldReturn404_whenScreeningDoesNotExist() throws Exception {

        UUID screeningId = UUID.randomUUID();

        mockMvc.perform(get("/screenings/{screeningId}", screeningId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title")
                        .value("Screening not found"))
                .andExpect(jsonPath("$.status")
                        .value(404));
    }

    @Test
    void getScreenings_shouldReturn400_whenMultipleFiltersAreProvided()
            throws Exception {

        mockMvc.perform(get("/screenings")
                        .param("movieId", UUID.randomUUID().toString())
                        .param("hallId", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title")
                        .value("Invalid screening filter"));
    }

    @Test
    void cancelScreening_shouldCancelScreening_whenScreeningIsScheduled()
            throws Exception {

        Screening screening = screeningRepository.saveAndFlush(createScreening());

        mockMvc.perform(patch("/screenings/{screeningId}/cancel",
                                screening.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status")
                        .value("CANCELLED"));

        Screening updated = screeningRepository
                .findById(screening.getId())
                .orElseThrow();

        assertThat(updated.getStatus())
                .isEqualTo(ScreeningStatus.CANCELLED);
    }

    @Test
    void cancelScreening_shouldReturn409_whenScreeningIsAlreadyCancelled()
            throws Exception {

        Screening screening = createScreening();
        screening.cancel();

        screeningRepository.saveAndFlush(screening);

        mockMvc.perform(patch("/screenings/{screeningId}/cancel",
                                screening.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                        .value("Screening already cancelled"));
    }

    @Test
    void createScreening_shouldReturn400_whenRequestIsInvalid() throws Exception {

        CreateScreeningRequest request = new CreateScreeningRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        LocalDateTime.now().minusDays(1),
                        new BigDecimal("-10"));

        mockMvc.perform(post("/screenings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(
                                jsonMapper.writeValueAsString(
                                        request)))
                .andExpect(status().isBadRequest());

        assertThat(screeningRepository.count())
                .isZero();
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
