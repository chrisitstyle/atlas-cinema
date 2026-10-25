package com.atlascinema.booking.repository;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    @Modifying
    @Query("""
        update Booking booking
        set booking.status = :expiredStatus
        where booking.status = :heldStatus
          and booking.expiresAt <= :now
        """)
    int expireBookings(
            @Param("now") LocalDateTime now,
            @Param("heldStatus") BookingStatus heldStatus,
            @Param("expiredStatus") BookingStatus expiredStatus
    );
}
