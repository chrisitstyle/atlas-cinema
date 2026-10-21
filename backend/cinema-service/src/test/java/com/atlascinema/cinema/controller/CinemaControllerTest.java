package com.atlascinema.cinema.controller;

import com.atlascinema.cinema.domain.SeatType;
import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.service.CinemaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CinemaControllerTest {

    @Mock
    private CinemaService cinemaService;

    @InjectMocks
    private CinemaController cinemaController;

    private UUID cinemaId;
    private UUID hallId;
    private UUID seatId;

    private CinemaResponse cinemaResponse;
    private HallResponse hallResponse;
    private SeatResponse seatResponse;

    @BeforeEach
    void setUp() {
        cinemaId = UUID.randomUUID();
        hallId = UUID.randomUUID();
        seatId = UUID.randomUUID();

        cinemaResponse = new CinemaResponse(
                cinemaId,
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10",
                true);

        hallResponse = new HallResponse(
                hallId,
                "Sala 1",
                cinemaId);

        seatResponse = new SeatResponse(
                seatId,
                hallId,
                "A",
                1,
                SeatType.STANDARD);
    }

    @Test
    void createCinema_shouldReturnCreatedCinema_whenRequestIsValid() {
        CreateCinemaRequest request = new CreateCinemaRequest(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10");

        when(cinemaService.createCinema(request))
                .thenReturn(cinemaResponse);

        ResponseEntity<CinemaResponse> response = cinemaController.createCinema(request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create("/cinemas/" + cinemaId));

        assertThat(response.getBody())
                .isEqualTo(cinemaResponse);

        verify(cinemaService).createCinema(request);
    }

    @Test
    void getCinemaById_shouldReturnCinema_whenCinemaExists() {
        when(cinemaService.getCinemaById(cinemaId))
                .thenReturn(cinemaResponse);

        CinemaResponse response = cinemaController.getCinemaById(cinemaId);

        assertThat(response).isEqualTo(cinemaResponse);
        assertThat(response.id()).isEqualTo(cinemaId);
        assertThat(response.name()).isEqualTo("Atlas Cinema");

        verify(cinemaService).getCinemaById(cinemaId);
    }

    @Test
    void getCinemas_shouldReturnAllCinemas_whenCinemasExist() {
        CinemaResponse secondCinema = new CinemaResponse(
                UUID.randomUUID(),
                "Atlas Cinema Kraków",
                "Kraków",
                "ul. Filmowa 20",
                true);

        when(cinemaService.getCinemas())
                .thenReturn(List.of(cinemaResponse, secondCinema));

        List<CinemaResponse> response = cinemaController.getCinemas();

        assertThat(response)
                .hasSize(2)
                .extracting(CinemaResponse::name)
                .containsExactly(
                        "Atlas Cinema",
                        "Atlas Cinema Kraków");

        verify(cinemaService).getCinemas();
    }

    @Test
    void getCinemas_shouldReturnEmptyList_whenNoCinemasExist() {
        when(cinemaService.getCinemas())
                .thenReturn(List.of());

        List<CinemaResponse> response = cinemaController.getCinemas();

        assertThat(response).isEmpty();

        verify(cinemaService).getCinemas();
    }

    @Test
    void addHall_shouldReturnCreatedHall_whenCinemaExists() {
        CreateHallRequest request = new CreateHallRequest("Sala 1");

        when(cinemaService.addHall(cinemaId, request))
                .thenReturn(hallResponse);

        ResponseEntity<HallResponse> response =
                cinemaController.addHall(cinemaId, request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create(
                        "/cinemas/" + cinemaId + "/halls/" + hallId));

        assertThat(response.getBody())
                .isEqualTo(hallResponse);

        verify(cinemaService).addHall(cinemaId, request);
    }

    @Test
    void getHalls_shouldReturnAllHalls_whenCinemaHasHalls() {
        HallResponse secondHall = new HallResponse(
                UUID.randomUUID(),
                "Sala 2",
                cinemaId);

        when(cinemaService.getHalls(cinemaId))
                .thenReturn(List.of(hallResponse, secondHall));

        List<HallResponse> response = cinemaController.getHalls(cinemaId);

        assertThat(response)
                .hasSize(2)
                .extracting(HallResponse::name)
                .containsExactly("Sala 1", "Sala 2");

        verify(cinemaService).getHalls(cinemaId);
    }

    @Test
    void getHalls_shouldReturnEmptyList_whenCinemaHasNoHalls() {
        when(cinemaService.getHalls(cinemaId))
                .thenReturn(List.of());

        List<HallResponse> response = cinemaController.getHalls(cinemaId);

        assertThat(response).isEmpty();

        verify(cinemaService).getHalls(cinemaId);
    }

    @Test
    void addSeat_shouldReturnCreatedSeat_whenSeatDoesNotExist() {
        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        when(cinemaService.addSeat(cinemaId, hallId, request))
                .thenReturn(seatResponse);

        ResponseEntity<SeatResponse> response = cinemaController.addSeat(cinemaId, hallId, request);

        assertThat(response.getStatusCode())
                .isEqualTo(HttpStatus.CREATED);

        assertThat(response.getHeaders().getLocation())
                .isEqualTo(URI.create(
                        "/cinemas/" + cinemaId
                                + "/halls/" + hallId
                                + "/seats/" + seatId));

        assertThat(response.getBody())
                .isEqualTo(seatResponse);

        verify(cinemaService).addSeat(cinemaId, hallId, request);
    }

    @Test
    void getSeatById_shouldReturnSeat_whenSeatExists() {
        when(cinemaService.getSeatById(cinemaId, hallId, seatId))
                .thenReturn(seatResponse);

        SeatResponse response = cinemaController.getSeatById(
                cinemaId, hallId, seatId);

        assertThat(response).isEqualTo(seatResponse);
        assertThat(response.id()).isEqualTo(seatId);
        assertThat(response.row()).isEqualTo("A");
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.type()).isEqualTo(SeatType.STANDARD);

        verify(cinemaService).getSeatById(cinemaId, hallId, seatId);
    }

    @Test
    void getSeats_shouldReturnAllSeats_whenHallHasSeats() {
        SeatResponse secondSeat = new SeatResponse(
                UUID.randomUUID(),
                hallId,
                "A",
                2,
                SeatType.VIP);

        when(cinemaService.getSeats(cinemaId, hallId))
                .thenReturn(List.of(seatResponse, secondSeat));

        List<SeatResponse> response = cinemaController.getSeats(cinemaId, hallId);

        assertThat(response)
                .hasSize(2)
                .extracting(SeatResponse::number)
                .containsExactly(1, 2);

        assertThat(response)
                .extracting(SeatResponse::type)
                .containsExactly(
                        SeatType.STANDARD,
                        SeatType.VIP);

        verify(cinemaService).getSeats(cinemaId, hallId);
    }

    @Test
    void getSeats_shouldReturnEmptyList_whenHallHasNoSeats() {
        when(cinemaService.getSeats(cinemaId, hallId))
                .thenReturn(List.of());

        List<SeatResponse> response = cinemaController.getSeats(cinemaId, hallId);

        assertThat(response).isEmpty();

        verify(cinemaService).getSeats(cinemaId, hallId);
    }

    @Test
    void getCinemaStructure_shouldReturnStructure_whenCinemaExists() {
        HallStructureResponse hallStructure = new HallStructureResponse(
                hallId,
                "Sala 1",
                List.of(seatResponse));

        CinemaStructureResponse structure = new CinemaStructureResponse(
                cinemaId,
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10",
                true,
                List.of(hallStructure));

        when(cinemaService.getCinemaStructure(cinemaId))
                .thenReturn(structure);

        CinemaStructureResponse response = cinemaController.getCinemaStructure(cinemaId);

        assertThat(response).isEqualTo(structure);
        assertThat(response.id()).isEqualTo(cinemaId);
        assertThat(response.halls()).hasSize(1);

        assertThat(response.halls().getFirst().seats())
                .containsExactly(seatResponse);

        verify(cinemaService).getCinemaStructure(cinemaId);
    }
}
