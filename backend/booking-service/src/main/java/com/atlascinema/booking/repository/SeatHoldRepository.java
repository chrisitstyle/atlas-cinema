package com.atlascinema.booking.repository;

import com.atlascinema.booking.domain.SeatHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SeatHoldRepository extends JpaRepository<SeatHold, UUID> {
}
