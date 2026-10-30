package com.atlascinema.booking.repository;

import com.atlascinema.booking.domain.Booking;
import com.atlascinema.booking.domain.BookingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BookingRepository extends JpaRepository<Booking, UUID> {

    // serialize confirmation with expiration and other booking updates
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT booking FROM Booking booking WHERE booking.id = :bookingId")
    Optional<Booking> findByIdForUpdate(@Param("bookingId") UUID bookingId);

    /*
     * Lock bookings selected for expiration so another scheduler instance
     * cannot process the same bookings at the same time
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select booking
            from Booking booking
            where booking.status = :heldStatus
              and booking.expiresAt <= :expirationCutoff
            order by booking.expiresAt asc
            """)
    List<Booking> findBookingsEligibleForExpiration(
            @Param("heldStatus") BookingStatus heldStatus,
            @Param("expirationCutoff") LocalDateTime expirationCutoff);

}
