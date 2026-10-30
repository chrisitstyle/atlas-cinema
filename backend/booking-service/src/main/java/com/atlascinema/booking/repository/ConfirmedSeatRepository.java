package com.atlascinema.booking.repository;

import com.atlascinema.booking.domain.ConfirmedSeat;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.UUID;

/*
 * Checks durable seat ownership independently from temporary Redis holds
 */
public interface ConfirmedSeatRepository extends JpaRepository<ConfirmedSeat, UUID> {

    boolean existsByScreeningIdAndSeatIdIn(
            UUID screeningId,
            Collection<UUID> seatIds);
}
