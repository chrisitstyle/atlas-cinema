package com.atlascinema.cinema.repository;

import com.atlascinema.cinema.domain.Hall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HallRepository extends JpaRepository<Hall, UUID> {

    Optional<Hall> findByIdAndCinema_Id(UUID hallId, UUID cinemaId);
    List<Hall> findAllByCinema_Id(UUID cinemaId);
}
