package com.atlascinema.cinema.service;

import com.atlascinema.cinema.domain.Cinema;
import com.atlascinema.cinema.domain.Hall;
import com.atlascinema.cinema.domain.Seat;
import com.atlascinema.cinema.dto.*;
import com.atlascinema.cinema.exception.CinemaNotFoundException;
import com.atlascinema.cinema.exception.HallNotFoundException;
import com.atlascinema.cinema.exception.SeatAlreadyExistsException;
import com.atlascinema.cinema.repository.CinemaRepository;
import com.atlascinema.cinema.repository.HallRepository;
import com.atlascinema.cinema.repository.SeatRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CinemaService {

    private final CinemaRepository cinemaRepository;
    private final HallRepository hallRepository;
    private final SeatRepository seatRepository;

    @Transactional
    public CinemaResponse createCinema(CreateCinemaRequest request) {
        Cinema cinema = Cinema.create(
                request.name(),
                request.city(),
                request.address());

        Cinema savedCinema = cinemaRepository.save(cinema);

        return CinemaResponse.from(savedCinema);
    }

    @Transactional
    public HallResponse addHall(
            UUID cinemaId,
            CreateHallRequest request
    ) {
        Cinema cinema = cinemaRepository.findById(cinemaId)
                .orElseThrow(() -> new CinemaNotFoundException(cinemaId));

        Hall hall = cinema.addHall(request.name());

        return HallResponse.from(hall);
    }

    @Transactional
    public SeatResponse addSeat(
            UUID cinemaId,
            UUID hallId,
            CreateSeatRequest request
    ) {
        Hall hall = hallRepository
                .findByIdAndCinema_Id(hallId, cinemaId)
                .orElseThrow(() -> new HallNotFoundException(hallId, cinemaId));

        boolean seatExists = seatRepository.existsByHall_IdAndRowAndNumber(
                        hallId,
                        request.row(),
                        request.number());

        if (seatExists) {
            throw new SeatAlreadyExistsException(
                    hallId,
                    request.row(),
                    request.number()
            );
        }

        Seat seat = hall.addSeat(
                request.row(),
                request.number(),
                request.type()
        );

        return SeatResponse.from(seat);
    }
}
