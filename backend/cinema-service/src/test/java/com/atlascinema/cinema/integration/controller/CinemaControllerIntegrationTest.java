package com.atlascinema.cinema.integration.controller;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Seat;
import com.atlascinema.cinema.domain.SeatType;
import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.integration.AbstractIntegrationTest;
import com.atlascinema.cinema.repository.CinemaRepository;
import com.atlascinema.cinema.repository.HallRepository;
import com.atlascinema.cinema.repository.SeatRepository;
import com.atlascinema.cinema.service.CinemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CinemaControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @Autowired
    private CinemaService cinemaService;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void createCinema_shouldReturn201_whenRequestIsValid() throws Exception {
        CreateCinemaRequest request = new CreateCinemaRequest(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10");

        MvcResult result = mockMvc.perform(post("/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Atlas Cinema"))
                .andExpect(jsonPath("$.city").value("Katowice"))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String location = result.getResponse().getHeader("Location");

        assertThat(location).startsWith("/cinemas/");

        UUID cinemaId = UUID.fromString(
                location.substring("/cinemas/".length()));

        Cinema saved = cinemaRepository.findById(cinemaId)
                .orElseThrow();

        assertThat(saved.getName()).isEqualTo("Atlas Cinema");
        assertThat(saved.getCity()).isEqualTo("Katowice");
        assertThat(saved.getAddress()).isEqualTo("ul. Kinowa 10");
    }

    @Test
    void createCinema_shouldReturn400_whenRequestIsInvalid() throws Exception {
        CreateCinemaRequest request = new CreateCinemaRequest(
                "",
                "Katowice",
                "ul. Kinowa 10");

        mockMvc.perform(post("/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(cinemaRepository.count()).isZero();
    }

    @Test
    void createCinema_shouldReturn400_whenNameExceedsMaxLength() throws Exception {
        CreateCinemaRequest request = new CreateCinemaRequest(
                "A".repeat(256),
                "Katowice",
                "ul. Kinowa 10");

        mockMvc.perform(post("/cinemas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(cinemaRepository.count()).isZero();
    }

    @Test
    void getCinemaById_shouldReturn200_whenCinemaExists() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");

        mockMvc.perform(get("/cinemas/{cinemaId}", cinema.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(cinema.getId().toString()))
                .andExpect(jsonPath("$.name").value("Atlas Cinema"))
                .andExpect(jsonPath("$.city").value("Katowice"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void getCinemaById_shouldReturn404_whenCinemaDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/cinemas/{cinemaId}", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Cinema not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(unknownId.toString())));
    }

    @Test
    void getCinemas_shouldReturn200_whenCinemasExist() throws Exception {
        createCinema("Atlas Cinema Katowice");
        createCinema("Atlas Cinema Kraków");

        mockMvc.perform(get("/cinemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name")
                        .value(containsInAnyOrder(
                                "Atlas Cinema Katowice",
                                "Atlas Cinema Kraków")));
    }

    @Test
    void getCinemas_shouldReturnEmptyList_whenNoCinemasExist() throws Exception {
        mockMvc.perform(get("/cinemas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void addHall_shouldReturn201_whenCinemaExists() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");

        CreateHallRequest request = new CreateHallRequest("Sala 1");

        MvcResult result = mockMvc.perform(
                        post("/cinemas/{cinemaId}/halls", cinema.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Sala 1"))
                .andExpect(jsonPath("$.cinemaId")
                        .value(cinema.getId().toString()))
                .andReturn();

        String location = result.getResponse().getHeader("Location");

        String expectedPrefix = "/cinemas/" + cinema.getId() + "/halls/";

        assertThat(location).startsWith(expectedPrefix);

        UUID hallId = UUID.fromString(
                location.substring(expectedPrefix.length()));

        assertThat(hallRepository.findById(hallId))
                .isPresent()
                .get()
                .satisfies(hall -> {
                    assertThat(hall.getName()).isEqualTo("Sala 1");
                    assertThat(hall.getCinema().getId())
                            .isEqualTo(cinema.getId());
                });
    }

    @Test
    void addHall_shouldReturn400_whenRequestIsInvalid() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");

        CreateHallRequest request = new CreateHallRequest("");

        mockMvc.perform(post("/cinemas/{cinemaId}/halls", cinema.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(hallRepository.count()).isZero();
    }

    @Test
    void addHall_shouldReturn400_whenNameExceedsMaxLength() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");

        CreateHallRequest request = new CreateHallRequest(
                "S".repeat(101)
        );

        mockMvc.perform(post("/cinemas/{cinemaId}/halls", cinema.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(hallRepository.count()).isZero();
    }

    @Test
    void addHall_shouldReturn404_whenCinemaDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        CreateHallRequest request = new CreateHallRequest("Sala 1");

        mockMvc.perform(post("/cinemas/{cinemaId}/halls", unknownId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Cinema not found"))
                .andExpect(jsonPath("$.status").value(404));

        assertThat(hallRepository.count()).isZero();
    }

    @Test
    void getHalls_shouldReturn200_whenCinemaHasHalls() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");

        createHall(cinema.getId(), "Sala 1");
        createHall(cinema.getId(), "Sala 2");

        mockMvc.perform(get("/cinemas/{cinemaId}/halls", cinema.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name")
                        .value(containsInAnyOrder("Sala 1", "Sala 2")));
    }

    @Test
    void getHalls_shouldReturn404_whenCinemaDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get("/cinemas/{cinemaId}/halls", unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Cinema not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void addSeat_shouldReturn201_whenRequestIsValid() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        MvcResult result = mockMvc.perform(
                        post("/cinemas/{cinemaId}/halls/{hallId}/seats",
                                cinema.getId(), hall.id())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.row").value("A"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.type").value("STANDARD"))
                .andReturn();

        String location = result.getResponse().getHeader("Location");

        String expectedPrefix = "/cinemas/" + cinema.getId()
                + "/halls/" + hall.id() + "/seats/";

        assertThat(location).startsWith(expectedPrefix);

        UUID seatId = UUID.fromString(
                location.substring(expectedPrefix.length()));

        Seat saved = seatRepository.findById(seatId)
                .orElseThrow();

        assertThat(saved.getRow()).isEqualTo("A");
        assertThat(saved.getNumber()).isEqualTo(1);
        assertThat(saved.getType()).isEqualTo(SeatType.STANDARD);
        assertThat(saved.getHall().getId()).isEqualTo(hall.id());
    }

    @Test
    void addSeat_shouldReturn400_whenRequestIsInvalid() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                -1,
                SeatType.STANDARD);

        mockMvc.perform(post("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        cinema.getId(), hall.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(seatRepository.count()).isZero();
    }

    @Test
    void addSeat_shouldReturn400_whenSeatTypeIsNull() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                null);

        mockMvc.perform(post("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        cinema.getId(), hall.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(seatRepository.count()).isZero();
    }

    @Test
    void addSeat_shouldReturn400_whenSeatRowIsBlank() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "",
                1,
                SeatType.STANDARD);

        mockMvc.perform(post("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        cinema.getId(), hall.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        assertThat(seatRepository.count()).isZero();
    }

    @Test
    void addSeat_shouldReturn404_whenHallBelongsToAnotherCinema() throws Exception {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        HallResponse hall = createHall(firstCinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        mockMvc.perform(post("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        secondCinema.getId(), hall.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Hall not found"));

        assertThat(seatRepository.count()).isZero();
    }

    @Test
    void addSeat_shouldReturn409_whenSeatAlreadyExists() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        createSeat(cinema.getId(), hall.id(), "A", 1, SeatType.STANDARD);

        CreateSeatRequest request = new CreateSeatRequest("A", 1, SeatType.VIP);

        mockMvc.perform(post("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        cinema.getId(), hall.id())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Seat already exists"))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.detail")
                        .value(containsString("A1")));

        assertThat(seatRepository.count()).isEqualTo(1);
    }

    @Test
    void getSeatById_shouldReturn200_whenSeatExists() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        SeatResponse seat = createSeat(
                cinema.getId(), hall.id(),
                "A", 1, SeatType.VIP);

        mockMvc.perform(get(
                        "/cinemas/{cinemaId}/halls/{hallId}/seats/{seatId}",
                        cinema.getId(), hall.id(), seat.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(seat.id().toString()))
                .andExpect(jsonPath("$.hallId")
                        .value(hall.id().toString()))
                .andExpect(jsonPath("$.row").value("A"))
                .andExpect(jsonPath("$.number").value(1))
                .andExpect(jsonPath("$.type").value("VIP"));
    }

    @Test
    void getSeatById_shouldReturn404_whenSeatDoesNotExist() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        UUID unknownSeatId = UUID.randomUUID();

        mockMvc.perform(get(
                        "/cinemas/{cinemaId}/halls/{hallId}/seats/{seatId}",
                        cinema.getId(), hall.id(), unknownSeatId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Seat not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(unknownSeatId.toString())));
    }

    @Test
    void getSeatById_shouldReturn404_whenSeatBelongsToAnotherHall() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");

        HallResponse firstHall = createHall(cinema.getId(), "Sala 1");
        HallResponse secondHall = createHall(cinema.getId(), "Sala 2");

        SeatResponse seat = createSeat(
                cinema.getId(),
                firstHall.id(),
                "A",
                1,
                SeatType.STANDARD);

        mockMvc.perform(get(
                        "/cinemas/{cinemaId}/halls/{hallId}/seats/{seatId}",
                        cinema.getId(),
                        secondHall.id(),
                        seat.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Seat not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(seat.id().toString())));

        assertThat(seatRepository.count()).isEqualTo(1);
    }

    @Test
    void getSeats_shouldReturn200_whenHallHasSeats() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        createSeat(cinema.getId(), hall.id(), "A", 1, SeatType.STANDARD);
        createSeat(cinema.getId(), hall.id(), "A", 2, SeatType.VIP);

        mockMvc.perform(get(
                        "/cinemas/{cinemaId}/halls/{hallId}/seats",
                        cinema.getId(), hall.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].number")
                        .value(containsInAnyOrder(1, 2)))
                .andExpect(jsonPath("$[*].type")
                        .value(containsInAnyOrder("STANDARD", "VIP")));
    }

    @Test
    void getSeats_shouldReturn404_whenHallDoesNotExist() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        UUID unknownHallId = UUID.randomUUID();

        mockMvc.perform(get("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        cinema.getId(), unknownHallId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Hall not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void getSeats_shouldReturn404_whenHallBelongsToAnotherCinema() throws Exception {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        HallResponse hall = createHall(firstCinema.getId(), "Sala 1");

        createSeat(firstCinema.getId(), hall.id(), "A", 1, SeatType.STANDARD);

        mockMvc.perform(get("/cinemas/{cinemaId}/halls/{hallId}/seats",
                        secondCinema.getId(),
                        hall.id()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Hall not found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail")
                        .value(containsString(hall.id().toString())));

        assertThat(seatRepository.count()).isEqualTo(1);
    }

    @Test
    void getCinemaStructure_shouldReturn200_whenCinemaExists() throws Exception {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        createSeat(cinema.getId(), hall.id(),
                "A", 1, SeatType.STANDARD);

        createSeat(cinema.getId(), hall.id(),
                "A", 2, SeatType.VIP);

        mockMvc.perform(get(
                        "/cinemas/{cinemaId}/structure",
                        cinema.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(cinema.getId().toString()))
                .andExpect(jsonPath("$.name").value("Atlas Cinema"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.halls.length()").value(1))
                .andExpect(jsonPath("$.halls[0].name").value("Sala 1"))
                .andExpect(jsonPath("$.halls[0].seats.length()").value(2))
                .andExpect(jsonPath("$.halls[0].seats[*].number")
                        .value(containsInAnyOrder(1, 2)))
                .andExpect(jsonPath("$.halls[0].seats[*].type")
                        .value(containsInAnyOrder("STANDARD", "VIP")));
    }

    @Test
    void getCinemaStructure_shouldReturn404_whenCinemaDoesNotExist() throws Exception {
        UUID unknownId = UUID.randomUUID();

        mockMvc.perform(get(
                        "/cinemas/{cinemaId}/structure",
                        unknownId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Cinema not found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    private Cinema createCinema(String name) {
        return cinemaRepository.saveAndFlush(
                Cinema.create(name, "Katowice", "ul. Kinowa 10"));
    }

    private HallResponse createHall(UUID cinemaId, String name) {
        return cinemaService.addHall(
                cinemaId,
                new CreateHallRequest(name));
    }

    private SeatResponse createSeat(
            UUID cinemaId,
            UUID hallId,
            String row,
            int number,
            SeatType type
    ) {
        return cinemaService.addSeat(
                cinemaId,
                hallId,
                new CreateSeatRequest(row, number, type));
    }
}