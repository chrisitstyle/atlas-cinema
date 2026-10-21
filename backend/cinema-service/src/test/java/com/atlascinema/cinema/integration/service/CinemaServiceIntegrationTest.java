package com.atlascinema.cinema.integration.service;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Hall;
import com.atlascinema.cinema.domain.Seat;
import com.atlascinema.cinema.domain.SeatType;
import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.exception.CinemaNotFoundException;
import com.atlascinema.cinema.exception.HallNotFoundException;
import com.atlascinema.cinema.exception.SeatAlreadyExistsException;
import com.atlascinema.cinema.exception.SeatNotFoundException;
import com.atlascinema.cinema.integration.AbstractIntegrationTest;
import com.atlascinema.cinema.repository.CinemaRepository;
import com.atlascinema.cinema.repository.HallRepository;
import com.atlascinema.cinema.repository.SeatRepository;
import com.atlascinema.cinema.service.CinemaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CinemaServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private CinemaService cinemaService;

    @Autowired
    private CinemaRepository cinemaRepository;

    @Autowired
    private HallRepository hallRepository;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    void createCinema_shouldPersistCinema_whenRequestIsValid() {
        CreateCinemaRequest request = new CreateCinemaRequest(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10");

        CinemaResponse response = cinemaService.createCinema(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Atlas Cinema");
        assertThat(response.city()).isEqualTo("Katowice");
        assertThat(response.address()).isEqualTo("ul. Kinowa 10");
        assertThat(response.active()).isTrue();

        Cinema saved = cinemaRepository.findById(response.id())
                .orElseThrow();

        assertThat(saved.getName()).isEqualTo("Atlas Cinema");
        assertThat(saved.getCity()).isEqualTo("Katowice");
        assertThat(saved.getAddress()).isEqualTo("ul. Kinowa 10");
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    void getCinemaById_shouldReturnCinema_whenCinemaExists() {
        Cinema cinema = createCinema("Atlas Cinema");

        CinemaResponse response = cinemaService.getCinemaById(
                cinema.getId());

        assertThat(response.id()).isEqualTo(cinema.getId());
        assertThat(response.name()).isEqualTo("Atlas Cinema");
        assertThat(response.city()).isEqualTo("Katowice");
        assertThat(response.active()).isTrue();
    }

    @Test
    void getCinemaById_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> cinemaService.getCinemaById(unknownId))
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void getCinemas_shouldReturnAllCinemas_whenCinemasExist() {
        createCinema("Atlas Cinema Katowice");
        createCinema("Atlas Cinema Kraków");

        List<CinemaResponse> response = cinemaService.getCinemas();

        assertThat(response)
                .hasSize(2)
                .extracting(CinemaResponse::name)
                .containsExactlyInAnyOrder(
                        "Atlas Cinema Katowice",
                        "Atlas Cinema Kraków");
    }

    @Test
    void getCinemas_shouldReturnEmptyList_whenNoCinemasExist() {
        List<CinemaResponse> response = cinemaService.getCinemas();

        assertThat(response).isEmpty();
    }

    @Test
    void addHall_shouldPersistHall_whenCinemaExists() {
        Cinema cinema = createCinema("Atlas Cinema");

        CreateHallRequest request = new CreateHallRequest("Sala 1");

        HallResponse response = cinemaService.addHall(
                cinema.getId(), request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Sala 1");
        assertThat(response.cinemaId()).isEqualTo(cinema.getId());

        Hall saved = hallRepository.findById(response.id())
                .orElseThrow();

        assertThat(saved.getName()).isEqualTo("Sala 1");
        assertThat(saved.getCinema().getId())
                .isEqualTo(cinema.getId());
    }

    @Test
    void addHall_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        CreateHallRequest request = new CreateHallRequest("Sala 1");

        assertThatThrownBy(() ->
                cinemaService.addHall(unknownId, request)
        )
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(unknownId.toString());

        assertThat(hallRepository.count()).isZero();
    }

    @Test
    void getHalls_shouldReturnAllHalls_whenCinemaHasHalls() {
        Cinema cinema = createCinema("Atlas Cinema");

        createHall(cinema.getId(), "Sala 1");
        createHall(cinema.getId(), "Sala 2");

        List<HallResponse> response = cinemaService.getHalls(
                cinema.getId());

        assertThat(response)
                .hasSize(2)
                .extracting(HallResponse::name)
                .containsExactlyInAnyOrder("Sala 1", "Sala 2");

        assertThat(response)
                .extracting(HallResponse::cinemaId)
                .containsOnly(cinema.getId());
    }

    @Test
    void getHalls_shouldReturnEmptyList_whenCinemaHasNoHalls() {
        Cinema cinema = createCinema("Atlas Cinema");

        List<HallResponse> response = cinemaService.getHalls(
                cinema.getId());

        assertThat(response).isEmpty();
    }

    @Test
    void getHalls_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() -> cinemaService.getHalls(unknownId))
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    @Test
    void addSeat_shouldPersistSeat_whenSeatDoesNotExist() {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        SeatResponse response = cinemaService.addSeat(
                cinema.getId(),
                hall.id(),
                request);

        assertThat(response.id()).isNotNull();
        assertThat(response.hallId()).isEqualTo(hall.id());
        assertThat(response.row()).isEqualTo("A");
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.type()).isEqualTo(SeatType.STANDARD);

        Seat saved = seatRepository.findById(response.id())
                .orElseThrow();

        assertThat(saved.getRow()).isEqualTo("A");
        assertThat(saved.getNumber()).isEqualTo(1);
        assertThat(saved.getType()).isEqualTo(SeatType.STANDARD);
        assertThat(saved.getHall().getId()).isEqualTo(hall.id());
    }

    @Test
    void addSeat_shouldThrowSeatAlreadyExistsException_whenSeatAlreadyExists() {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        createSeat(
                cinema.getId(),
                hall.id(),
                "A",
                1,
                SeatType.STANDARD);

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.VIP);

        assertThatThrownBy(() -> cinemaService.addSeat(
                cinema.getId(),
                hall.id(),
                request
        ))
                .isInstanceOf(SeatAlreadyExistsException.class)
                .hasMessageContaining("A1")
                .hasMessageContaining(hall.id().toString());

        assertThat(seatRepository.count()).isEqualTo(1);
    }

    @Test
    void addSeat_shouldThrowHallNotFoundException_whenHallBelongsToAnotherCinema() {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        HallResponse hall = createHall(firstCinema.getId(), "Sala 1");

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        assertThatThrownBy(() -> cinemaService.addSeat(
                secondCinema.getId(),
                hall.id(),
                request
        ))
                .isInstanceOf(HallNotFoundException.class)
                .hasMessageContaining(hall.id().toString())
                .hasMessageContaining(secondCinema.getId().toString());

        assertThat(seatRepository.count()).isZero();
    }

    @Test
    void addSeat_shouldAllowSameCoordinates_whenSeatsBelongToDifferentHalls() {
        Cinema cinema = createCinema("Atlas Cinema");

        HallResponse firstHall = createHall(cinema.getId(), "Sala 1");
        HallResponse secondHall = createHall(cinema.getId(), "Sala 2");

        SeatResponse firstSeat = createSeat(
                cinema.getId(),
                firstHall.id(),
                "A",
                1,
                SeatType.STANDARD);

        SeatResponse secondSeat = createSeat(
                cinema.getId(),
                secondHall.id(),
                "A",
                1,
                SeatType.VIP);

        assertThat(firstSeat.id()).isNotEqualTo(secondSeat.id());
        assertThat(firstSeat.hallId()).isNotEqualTo(secondSeat.hallId());

        assertThat(seatRepository.count()).isEqualTo(2);

        assertThat(seatRepository.existsByHall_IdAndRowAndNumber(
                firstHall.id(), "A", 1
        )).isTrue();

        assertThat(seatRepository.existsByHall_IdAndRowAndNumber(
                secondHall.id(), "A", 1
        )).isTrue();
    }

    @Test
    void getSeatById_shouldReturnSeat_whenSeatExists() {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        SeatResponse saved = createSeat(
                cinema.getId(),
                hall.id(),
                "A",
                1,
                SeatType.VIP);

        SeatResponse response = cinemaService.getSeatById(
                cinema.getId(),
                hall.id(),
                saved.id());

        assertThat(response.id()).isEqualTo(saved.id());
        assertThat(response.hallId()).isEqualTo(hall.id());
        assertThat(response.row()).isEqualTo("A");
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.type()).isEqualTo(SeatType.VIP);
    }

    @Test
    void getSeatById_shouldThrowSeatNotFoundException_whenSeatDoesNotExist() {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        UUID unknownSeatId = UUID.randomUUID();

        assertThatThrownBy(() -> cinemaService.getSeatById(
                cinema.getId(),
                hall.id(),
                unknownSeatId
        ))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining(unknownSeatId.toString())
                .hasMessageContaining(hall.id().toString())
                .hasMessageContaining(cinema.getId().toString());
    }

    @Test
    void getSeatById_shouldThrowSeatNotFoundException_whenSeatBelongsToAnotherCinema() {
        Cinema firstCinema = createCinema("Atlas Cinema Katowice");
        Cinema secondCinema = createCinema("Atlas Cinema Kraków");

        HallResponse hall = createHall(firstCinema.getId(), "Sala 1");

        SeatResponse seat = createSeat(
                firstCinema.getId(),
                hall.id(),
                "A",
                1,
                SeatType.STANDARD);

        assertThatThrownBy(() -> cinemaService.getSeatById(
                secondCinema.getId(),
                hall.id(),
                seat.id()
        ))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining(seat.id().toString());
    }

    @Test
    void getSeats_shouldReturnAllSeats_whenHallHasSeats() {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        createSeat(
                cinema.getId(), hall.id(),
                "A", 1, SeatType.STANDARD);

        createSeat(
                cinema.getId(), hall.id(),
                "A", 2, SeatType.PREMIUM);

        createSeat(
                cinema.getId(), hall.id(),
                "B", 1, SeatType.VIP);

        List<SeatResponse> response = cinemaService.getSeats(
                cinema.getId(),
                hall.id());

        assertThat(response).hasSize(3);

        assertThat(response)
                .extracting(SeatResponse::type)
                .containsExactlyInAnyOrder(
                        SeatType.STANDARD,
                        SeatType.PREMIUM,
                        SeatType.VIP);

        assertThat(response)
                .extracting(SeatResponse::row)
                .containsExactlyInAnyOrder("A", "A", "B");

        assertThat(response)
                .extracting(SeatResponse::hallId)
                .containsOnly(hall.id());
    }

    @Test
    void getSeats_shouldReturnEmptyList_whenHallHasNoSeats() {
        Cinema cinema = createCinema("Atlas Cinema");
        HallResponse hall = createHall(cinema.getId(), "Sala 1");

        List<SeatResponse> response = cinemaService.getSeats(
                cinema.getId(),
                hall.id());

        assertThat(response).isEmpty();
    }

    @Test
    void getSeats_shouldThrowHallNotFoundException_whenHallDoesNotExist() {
        Cinema cinema = createCinema("Atlas Cinema");

        UUID unknownHallId = UUID.randomUUID();

        assertThatThrownBy(() -> cinemaService.getSeats(
                cinema.getId(),
                unknownHallId
        ))
                .isInstanceOf(HallNotFoundException.class)
                .hasMessageContaining(unknownHallId.toString())
                .hasMessageContaining(cinema.getId().toString());
    }

    @Test
    void getCinemaStructure_shouldReturnCompleteStructure_whenCinemaHasHallsAndSeats() {
        Cinema cinema = createCinema("Atlas Cinema");

        HallResponse firstHall = createHall(
                cinema.getId(), "Sala 1");

        HallResponse secondHall = createHall(
                cinema.getId(), "Sala 2");

        createSeat(
                cinema.getId(), firstHall.id(),
                "A", 1, SeatType.STANDARD);

        createSeat(
                cinema.getId(), firstHall.id(),
                "A", 2, SeatType.VIP);

        createSeat(
                cinema.getId(), secondHall.id(),
                "B", 1, SeatType.PREMIUM);

        CinemaStructureResponse response = cinemaService.getCinemaStructure(cinema.getId());

        assertThat(response.id()).isEqualTo(cinema.getId());
        assertThat(response.name()).isEqualTo("Atlas Cinema");
        assertThat(response.city()).isEqualTo("Katowice");
        assertThat(response.active()).isTrue();

        assertThat(response.halls())
                .hasSize(2)
                .extracting(HallStructureResponse::name)
                .containsExactlyInAnyOrder("Sala 1", "Sala 2");

        HallStructureResponse firstHallResponse = response.halls()
                .stream()
                .filter(hall -> hall.name().equals("Sala 1"))
                .findFirst()
                .orElseThrow();

        assertThat(firstHallResponse.seats())
                .hasSize(2)
                .extracting(SeatResponse::type)
                .containsExactlyInAnyOrder(
                        SeatType.STANDARD,
                        SeatType.VIP);

        HallStructureResponse secondHallResponse = response.halls()
                .stream()
                .filter(hall -> hall.name().equals("Sala 2"))
                .findFirst()
                .orElseThrow();

        assertThat(secondHallResponse.seats())
                .hasSize(1)
                .first()
                .satisfies(seat -> {
                    assertThat(seat.row()).isEqualTo("B");
                    assertThat(seat.number()).isEqualTo(1);
                    assertThat(seat.type()).isEqualTo(SeatType.PREMIUM);
                });
    }

    @Test
    void getCinemaStructure_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        UUID unknownId = UUID.randomUUID();

        assertThatThrownBy(() ->
                cinemaService.getCinemaStructure(unknownId)
        )
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    private Cinema createCinema(String name) {
        return cinemaRepository.saveAndFlush(
                Cinema.create(
                        name,
                        "Katowice",
                        "ul. Kinowa 10"));
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
