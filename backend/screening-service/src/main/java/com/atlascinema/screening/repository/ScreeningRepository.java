package com.atlascinema.screening.repository;

import com.atlascinema.screening.domain.Screening;
import com.atlascinema.screening.domain.ScreeningStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ScreeningRepository
        extends JpaRepository<Screening, UUID> {

    @Query("""
            select count(s)
            from Screening s
            where s.hallId = :hallId
              and s.status = :status
              and s.startsAt < :windowEnd
              and s.endsAt > :windowStart
            """)
    long countConflictingScreenings(
            @Param("hallId") UUID hallId,
            @Param("status") ScreeningStatus status,
            @Param("windowStart") LocalDateTime windowStart,
            @Param("windowEnd") LocalDateTime windowEnd
    );
}
