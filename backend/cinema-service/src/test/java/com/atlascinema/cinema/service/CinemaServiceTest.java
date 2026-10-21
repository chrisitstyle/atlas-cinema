package com.atlascinema.cinema.service;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Hall;
import com.atlascinema.cinema.domain.Seat;
import com.atlascinema.cinema.domain.SeatType;
import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.exception.CinemaNotFoundException;
import com.atlascinema.cinema.exception.HallNotFoundException;
import com.atlascinema.cinema.exception.SeatAlreadyExistsException;
import com.atlascinema.cinema.exception.SeatNotFoundException;
import com.atlascinema.cinema.repository.CinemaRepository;
import com.atlascinema.cinema.repository.HallRepository;
import com.atlascinema.cinema.repository.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CinemaServiceTest {

    @Mock
    private CinemaRepository cinemaRepository;

    @Mock
    private HallRepository hallRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private CinemaService cinemaService;

    private Cinema cinema;
    private UUID cinemaId;

    @BeforeEach
    void setUp() {
        cinema = Cinema.create(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10");

        cinemaId = cinema.getId();
    }

    @Test
    void createCinema_shouldCreateCinema_whenRequestIsValid() {
        CreateCinemaRequest request = new CreateCinemaRequest(
                "Atlas Cinema",
                "Katowice",
                "ul. Kinowa 10"
        );

        when(cinemaRepository.save(any(Cinema.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CinemaResponse response = cinemaService.createCinema(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Atlas Cinema");
        assertThat(response.city()).isEqualTo("Katowice");
        assertThat(response.address()).isEqualTo("ul. Kinowa 10");
        assertThat(response.active()).isTrue();

        verify(cinemaRepository).save(any(Cinema.class));
    }

    @Test
    void getCinemaById_shouldReturnCinema_whenCinemaExists() {
        when(cinemaRepository.findById(cinemaId))
                .thenReturn(Optional.of(cinema));

        CinemaResponse response = cinemaService.getCinemaById(cinemaId);

        assertThat(response.id()).isEqualTo(cinemaId);
        assertThat(response.name()).isEqualTo("Atlas Cinema");
        assertThat(response.active()).isTrue();

        verify(cinemaRepository).findById(cinemaId);
    }

    @Test
    void getCinemaById_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        when(cinemaRepository.findById(cinemaId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cinemaService.getCinemaById(cinemaId))
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(cinemaId.toString());
    }

    @Test
    void getCinemas_shouldReturnAllCinemas_whenCinemasExist() {
        Cinema secondCinema = Cinema.create(
                "Atlas Cinema Kraków",
                "Kraków",
                "ul. Filmowa 20"
        );

        when(cinemaRepository.findAll())
                .thenReturn(List.of(cinema, secondCinema));

        List<CinemaResponse> response = cinemaService.getCinemas();

        assertThat(response)
                .hasSize(2)
                .extracting(CinemaResponse::name)
                .containsExactly("Atlas Cinema", "Atlas Cinema Kraków");

        verify(cinemaRepository).findAll();
    }

    @Test
    void getCinemas_shouldReturnEmptyList_whenNoCinemasExist() {
        when(cinemaRepository.findAll())
                .thenReturn(List.of());

        List<CinemaResponse> response = cinemaService.getCinemas();

        assertThat(response).isEmpty();

        verify(cinemaRepository).findAll();
    }

    @Test
    void addHall_shouldAddHall_whenCinemaExists() {
        CreateHallRequest request = new CreateHallRequest("Sala 1");

        when(cinemaRepository.findById(cinemaId))
                .thenReturn(Optional.of(cinema));

        HallResponse response = cinemaService.addHall(cinemaId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.name()).isEqualTo("Sala 1");
        assertThat(response.cinemaId()).isEqualTo(cinemaId);

        assertThat(cinema.getHalls())
                .hasSize(1)
                .extracting(Hall::getName)
                .containsExactly("Sala 1");

        verify(cinemaRepository).findById(cinemaId);
        verify(hallRepository, never()).save(any());
    }

    @Test
    void addHall_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        CreateHallRequest request = new CreateHallRequest("Sala 1");

        when(cinemaRepository.findById(cinemaId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cinemaService.addHall(cinemaId, request))
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(cinemaId.toString());

        verifyNoInteractions(hallRepository);
    }

    @Test
    void getHalls_shouldReturnAllHalls_whenCinemaExists() {
        Hall firstHall = cinema.addHall("Sala 1");
        Hall secondHall = cinema.addHall("Sala 2");

        when(cinemaRepository.existsById(cinemaId))
                .thenReturn(true);

        when(hallRepository.findAllByCinema_Id(cinemaId))
                .thenReturn(List.of(firstHall, secondHall));

        List<HallResponse> response = cinemaService.getHalls(cinemaId);

        assertThat(response)
                .hasSize(2)
                .extracting(HallResponse::name)
                .containsExactly("Sala 1", "Sala 2");

        assertThat(response)
                .extracting(HallResponse::cinemaId)
                .containsOnly(cinemaId);

        verify(cinemaRepository).existsById(cinemaId);
        verify(hallRepository).findAllByCinema_Id(cinemaId);
    }

    @Test
    void getHalls_shouldReturnEmptyList_whenCinemaHasNoHalls() {
        when(cinemaRepository.existsById(cinemaId))
                .thenReturn(true);

        when(hallRepository.findAllByCinema_Id(cinemaId))
                .thenReturn(List.of());

        List<HallResponse> response = cinemaService.getHalls(cinemaId);

        assertThat(response).isEmpty();
    }

    @Test
    void getHalls_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        when(cinemaRepository.existsById(cinemaId))
                .thenReturn(false);

        assertThatThrownBy(() -> cinemaService.getHalls(cinemaId))
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(cinemaId.toString());

        verifyNoInteractions(hallRepository);
    }

    @Test
    void addSeat_shouldAddSeat_whenHallExistsAndSeatIsAvailable() {
        Hall hall = cinema.addHall("Sala 1");
        UUID hallId = hall.getId();

        CreateSeatRequest request = new CreateSeatRequest("A", 1, SeatType.STANDARD);

        when(hallRepository.findByIdAndCinema_Id(hallId, cinemaId))
                .thenReturn(Optional.of(hall));

        when(seatRepository.existsByHall_IdAndRowAndNumber(
                hallId, "A", 1
        )).thenReturn(false);

        SeatResponse response = cinemaService.addSeat(
                cinemaId, hallId, request);

        assertThat(response.id()).isNotNull();
        assertThat(response.hallId()).isEqualTo(hallId);
        assertThat(response.row()).isEqualTo("A");
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.type()).isEqualTo(SeatType.STANDARD);

        assertThat(hall.getSeats())
                .hasSize(1)
                .extracting(Seat::getId)
                .containsExactly(response.id());

        verify(seatRepository)
                .existsByHall_IdAndRowAndNumber(hallId, "A", 1);

        verify(seatRepository, never()).save(any());
    }

    @Test
    void addSeat_shouldThrowHallNotFoundException_whenHallDoesNotExist() {
        UUID unknownHallId = UUID.randomUUID();

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        when(hallRepository.findByIdAndCinema_Id(
                unknownHallId, cinemaId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cinemaService.addSeat(
                cinemaId, unknownHallId, request
        ))
                .isInstanceOf(HallNotFoundException.class)
                .hasMessageContaining(unknownHallId.toString());

        verifyNoInteractions(seatRepository);
    }

    @Test
    void addSeat_shouldThrowSeatAlreadyExistsException_whenSeatIsTaken() {
        Hall hall = cinema.addHall("Sala 1");
        UUID hallId = hall.getId();

        CreateSeatRequest request = new CreateSeatRequest(
                "A",
                1,
                SeatType.STANDARD);

        when(hallRepository.findByIdAndCinema_Id(hallId, cinemaId))
                .thenReturn(Optional.of(hall));

        when(seatRepository.existsByHall_IdAndRowAndNumber(
                hallId, "A", 1
        )).thenReturn(true);

        assertThatThrownBy(() -> cinemaService.addSeat(
                cinemaId, hallId, request
        ))
                .isInstanceOf(SeatAlreadyExistsException.class)
                .hasMessageContaining("A1")
                .hasMessageContaining(hallId.toString());

        assertThat(hall.getSeats()).isEmpty();

        verify(seatRepository)
                .existsByHall_IdAndRowAndNumber(hallId, "A", 1);

        verify(seatRepository, never()).save(any());
    }

    @Test
    void getSeatById_shouldReturnSeat_whenSeatExists() {
        Hall hall = cinema.addHall("Sala 1");
        Seat seat = hall.addSeat("A", 1, SeatType.VIP);

        UUID hallId = hall.getId();
        UUID seatId = seat.getId();

        when(seatRepository.findByIdAndHall_IdAndHall_Cinema_Id(
                seatId, hallId, cinemaId
        )).thenReturn(Optional.of(seat));

        SeatResponse response = cinemaService.getSeatById(
                cinemaId, hallId, seatId);

        assertThat(response.id()).isEqualTo(seatId);
        assertThat(response.hallId()).isEqualTo(hallId);
        assertThat(response.row()).isEqualTo("A");
        assertThat(response.number()).isEqualTo(1);
        assertThat(response.type()).isEqualTo(SeatType.VIP);

        verify(seatRepository)
                .findByIdAndHall_IdAndHall_Cinema_Id(
                        seatId, hallId, cinemaId
                );
    }

    @Test
    void getSeatById_shouldThrowSeatNotFoundException_whenSeatDoesNotExist() {
        UUID hallId = UUID.randomUUID();
        UUID seatId = UUID.randomUUID();

        when(seatRepository.findByIdAndHall_IdAndHall_Cinema_Id(
                seatId, hallId, cinemaId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cinemaService.getSeatById(
                cinemaId, hallId, seatId
        ))
                .isInstanceOf(SeatNotFoundException.class)
                .hasMessageContaining(seatId.toString())
                .hasMessageContaining(hallId.toString())
                .hasMessageContaining(cinemaId.toString());
    }

    @Test
    void getSeats_shouldReturnAllSeats_whenHallExists() {
        Hall hall = cinema.addHall("Sala 1");
        UUID hallId = hall.getId();

        Seat firstSeat = hall.addSeat("A", 1, SeatType.STANDARD);
        Seat secondSeat = hall.addSeat("A", 2, SeatType.PREMIUM);

        when(hallRepository.findByIdAndCinema_Id(hallId, cinemaId))
                .thenReturn(Optional.of(hall));

        when(seatRepository.findAllByHall_Id(hallId))
                .thenReturn(List.of(firstSeat, secondSeat));

        List<SeatResponse> response = cinemaService.getSeats(
                cinemaId, hallId);

        assertThat(response)
                .hasSize(2)
                .extracting(SeatResponse::number)
                .containsExactly(1, 2);

        assertThat(response)
                .extracting(SeatResponse::type)
                .containsExactly(SeatType.STANDARD, SeatType.PREMIUM);

        verify(seatRepository).findAllByHall_Id(hallId);
    }

    @Test
    void getSeats_shouldReturnEmptyList_whenHallHasNoSeats() {
        Hall hall = cinema.addHall("Sala 1");
        UUID hallId = hall.getId();

        when(hallRepository.findByIdAndCinema_Id(hallId, cinemaId))
                .thenReturn(Optional.of(hall));

        when(seatRepository.findAllByHall_Id(hallId))
                .thenReturn(List.of());

        List<SeatResponse> response = cinemaService.getSeats(
                cinemaId, hallId
        );

        assertThat(response).isEmpty();
    }

    @Test
    void getSeats_shouldThrowHallNotFoundException_whenHallDoesNotExist() {
        UUID unknownHallId = UUID.randomUUID();

        when(hallRepository.findByIdAndCinema_Id(
                unknownHallId, cinemaId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cinemaService.getSeats(
                cinemaId, unknownHallId
        ))
                .isInstanceOf(HallNotFoundException.class)
                .hasMessageContaining(unknownHallId.toString());

        verifyNoInteractions(seatRepository);
    }

    @Test
    void getCinemaStructure_shouldReturnCompleteStructure_whenCinemaExists() {
        Hall firstHall = cinema.addHall("Sala 1");
        firstHall.addSeat("A", 1, SeatType.STANDARD);
        firstHall.addSeat("A", 2, SeatType.VIP);

        Hall secondHall = cinema.addHall("Sala 2");
        secondHall.addSeat("B", 1, SeatType.PREMIUM);

        when(cinemaRepository.findWithHallsById(cinemaId))
                .thenReturn(Optional.of(cinema));

        CinemaStructureResponse response =
                cinemaService.getCinemaStructure(cinemaId);

        assertThat(response.id()).isEqualTo(cinemaId);
        assertThat(response.name()).isEqualTo("Atlas Cinema");

        assertThat(response.halls())
                .hasSize(2)
                .extracting(HallStructureResponse::name)
                .containsExactly("Sala 1", "Sala 2");

        assertThat(response.halls().get(0).seats())
                .hasSize(2)
                .extracting(SeatResponse::number)
                .containsExactly(1, 2);

        assertThat(response.halls().get(1).seats())
                .hasSize(1)
                .first()
                .satisfies(seat -> {
                    assertThat(seat.row()).isEqualTo("B");
                    assertThat(seat.type()).isEqualTo(SeatType.PREMIUM);
                });

        verify(cinemaRepository).findWithHallsById(cinemaId);
    }

    @Test
    void getCinemaStructure_shouldThrowCinemaNotFoundException_whenCinemaDoesNotExist() {
        when(cinemaRepository.findWithHallsById(cinemaId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> cinemaService.getCinemaStructure(cinemaId))
                .isInstanceOf(CinemaNotFoundException.class)
                .hasMessageContaining(cinemaId.toString());
    }
}
