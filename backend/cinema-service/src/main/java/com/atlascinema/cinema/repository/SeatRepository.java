package com.atlascinema.cinema.repository;

import com.atlascinema.cinema.domain.Seat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SeatRepository extends JpaRepository<Seat, UUID> {

    boolean existsByHall_IdAndRowAndNumber(UUID hallId, String row, Integer number);
}
