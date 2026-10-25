package com.atlascinema.booking.integration.controller;

import com.atlascinema.booking.client.CinemaClient;
import com.atlascinema.booking.client.ScreeningClient;
import com.atlascinema.booking.client.dto.ScreeningClientResponse;
import com.atlascinema.booking.client.dto.ScreeningClientStatus;
import com.atlascinema.booking.client.dto.SeatClientResponse;
import com.atlascinema.booking.dto.CreateBookingRequest;
import com.atlascinema.booking.integration.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BookingControllerIntegrationTest extends AbstractIntegrationTest {

    private static final Instant NOW = Instant.parse("2030-01-10T12:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ScreeningClient screeningClient;

    @MockitoBean
    private CinemaClient cinemaClient;

    @MockitoBean
    private Clock clock;

    private UUID screeningId;
    private UUID hallId;
    private UUID seatId;

    @BeforeEach
    void setUp() {
        screeningId = UUID.randomUUID();
        hallId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        when(clock.instant())
                .thenReturn(NOW);

        when(clock.getZone())
                .thenReturn(ZoneOffset.UTC);
    }

    @Test
    void createBooking_shouldReturnCreated_whenRequestIsValid()
            throws Exception {

        mockAvailableScreening();
        mockExistingSeat(seatId);

        CreateBookingRequest request = validRequest();

        mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.customerId").value(nullValue()))
                .andExpect(jsonPath("$.contactEmail")
                                .value("guest@example.com"))
                .andExpect(jsonPath("$.screeningId")
                                .value(screeningId.toString()))
                .andExpect(jsonPath("$.status")
                                .value("HELD"))
                .andExpect(jsonPath("$.createdAt")
                                .value("2030-01-10T12:00:00"))
                .andExpect(jsonPath("$.expiresAt")
                                .value("2030-01-10T12:10:00"))
                .andExpect(jsonPath("$.totalPrice")
                                .value(29.90))
                .andExpect(jsonPath("$.seats.length()")
                                .value(1))
                .andExpect(jsonPath("$.seats[0].id")
                                .exists())
                .andExpect(jsonPath("$.seats[0].seatId")
                                .value(seatId.toString()))
                .andExpect(jsonPath("$.seats[0].price")
                                .value(29.90));

        verify(screeningClient).getScreening(screeningId);

        verify(cinemaClient).getSeat(hallId, seatId);
    }

    @Test
    void createBooking_shouldReturnBadRequest_whenEmailIsInvalid() throws Exception {

        CreateBookingRequest request = new CreateBookingRequest(
                        null,
                        "not-an-email",
                        screeningId,
                        List.of(seatId));

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(screeningClient);
        verifyNoInteractions(cinemaClient);
    }

    @Test
    void createBooking_shouldReturnBadRequest_whenSeatIdsAreEmpty() throws Exception {

        CreateBookingRequest request = new CreateBookingRequest(
                        null,
                        "guest@example.com",
                        screeningId,
                        List.of());

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(screeningClient);
        verifyNoInteractions(cinemaClient);
    }

    @Test
    void createBooking_shouldReturnBadRequest_whenScreeningIdIsNull() throws Exception {

        CreateBookingRequest request = new CreateBookingRequest(
                        null,
                        "guest@example.com",
                        null,
                        List.of(seatId));

        mockMvc.perform(post("/bookings").contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(screeningClient);
        verifyNoInteractions(cinemaClient);
    }

    @Test
    void createBooking_shouldReturnNotFound_whenScreeningDoesNotExist() throws Exception {

        FeignException.NotFound exception = mock(FeignException.NotFound.class);

        when(screeningClient.getScreening(screeningId))
                .thenThrow(exception);

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title")
                                .value("Screening not found"))
                .andExpect(jsonPath("$.status")
                                .value(404))
                .andExpect(jsonPath("$.detail")
                                .value("Screening with id "
                                                + screeningId
                                                + " was not found"));

        verify(screeningClient).getScreening(screeningId);

        verifyNoInteractions(cinemaClient);
    }

    @Test
    void createBooking_shouldReturnConflict_whenScreeningIsCancelled() throws Exception {

        when(screeningClient.getScreening(screeningId))
                .thenReturn(new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                now().plusHours(2),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.CANCELLED));

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                                .value("Screening unavailable"))
                .andExpect(jsonPath("$.status")
                                .value(409));

        verifyNoInteractions(cinemaClient);
    }

    @Test
    void createBooking_shouldReturnConflict_whenScreeningHasAlreadyStarted()
            throws Exception {

        when(screeningClient.getScreening(screeningId))
                .thenReturn(
                        new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                now().minusMinutes(1),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.SCHEDULED));

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                                .value("Screening unavailable"))
                .andExpect(jsonPath("$.status")
                                .value(409));

        verifyNoInteractions(cinemaClient);
    }

    @Test
    void createBooking_shouldReturnNotFound_whenSeatDoesNotExist() throws Exception {

        mockAvailableScreening();

        FeignException.NotFound exception = mock(FeignException.NotFound.class);

        when(cinemaClient.getSeat(
                hallId,
                seatId
        )).thenThrow(exception);

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title")
                                .value("Seat not found"))
                .andExpect(jsonPath("$.status")
                                .value(404))
                .andExpect(jsonPath("$.detail")
                                .value(
                                        "Seat with id "
                                                + seatId
                                                + " was not found in screening hall"));
    }

    @Test
    void createBooking_shouldCreateBookingWithMultipleSeats_whenSeatsAreValid() throws Exception {

        UUID secondSeatId = UUID.randomUUID();
        UUID thirdSeatId = UUID.randomUUID();

        mockAvailableScreening();

        mockExistingSeat(seatId);
        mockExistingSeat(secondSeatId);
        mockExistingSeat(thirdSeatId);

        CreateBookingRequest request = new CreateBookingRequest(null,
                        "guest@example.com",
                        screeningId, List.of(
                                seatId,
                                secondSeatId,
                                thirdSeatId));

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seats.length()")
                                .value(3))
                .andExpect(jsonPath("$.totalPrice")
                                .value(89.70))
                .andExpect(jsonPath("$.seats[0].seatId")
                                .value(seatId.toString()))
                .andExpect(jsonPath("$.seats[1].seatId")
                                .value(secondSeatId.toString()))
                .andExpect(jsonPath("$.seats[2].seatId")
                                .value(thirdSeatId.toString()));
    }

    @Test
    void createBooking_shouldReturnConflict_whenSeatIsAlreadyReserved() throws Exception {

        mockAvailableScreening();
        mockExistingSeat(seatId);

        CreateBookingRequest request = validRequest();

        String requestJson = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/bookings")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title")
                                .value("Seat already reserved"))
                .andExpect(jsonPath("$.status")
                                .value(409))
                .andExpect(jsonPath("$.detail")
                                .value(
                                        "One or more selected seats are already reserved "
                                                + "for screening "
                                                + screeningId));
    }

    private CreateBookingRequest validRequest() {
        return new CreateBookingRequest(
                null,
                "guest@example.com",
                screeningId,
                List.of(seatId));
    }

    private void mockAvailableScreening() {
        when(screeningClient.getScreening(screeningId))
                .thenReturn(
                        new ScreeningClientResponse(
                                screeningId,
                                hallId,
                                now().plusHours(2),
                                new BigDecimal("29.90"),
                                ScreeningClientStatus.SCHEDULED));
    }

    private void mockExistingSeat(UUID currentSeatId) {
        when(cinemaClient.getSeat(
                hallId,
                currentSeatId
        )).thenReturn(new SeatClientResponse(
                        currentSeatId,
                        hallId));
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC);
    }
}
