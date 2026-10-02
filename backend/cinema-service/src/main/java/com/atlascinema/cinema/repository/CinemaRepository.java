package com.atlascinema.cinema.repository;

import com.atlascinema.cinema.domain.Cinema;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CinemaRepository extends JpaRepository<Cinema, UUID> {

    @EntityGraph(attributePaths = "halls")
    Optional<Cinema> findWithHallsById(UUID id);
}
