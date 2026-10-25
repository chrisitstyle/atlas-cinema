package com.atlascinema.screening.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "screenings")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Screening {

    @Id
    private UUID id;

    @Column(name = "movie_id", nullable = false)
    private UUID movieId;

    @Column(name = "hall_id", nullable = false)
    private UUID hallId;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private LocalDateTime endsAt;

    @Column(
            name = "base_price",
            nullable = false,
            precision = 10,
            scale = 2
    )
    private BigDecimal basePrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScreeningStatus status;

    private Screening(
            UUID id,
            UUID movieId,
            UUID hallId,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            BigDecimal basePrice,
            ScreeningStatus status
    ) {
        this.id = id;
        this.movieId = movieId;
        this.hallId = hallId;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.basePrice = basePrice;
        this.status = status;
    }

    public static Screening create(
            UUID movieId,
            UUID hallId,
            LocalDateTime startsAt,
            Integer durationMinutes,
            BigDecimal basePrice) {

        LocalDateTime endsAt = startsAt.plusMinutes(durationMinutes);

        return new Screening(
                UUID.randomUUID(),
                movieId,
                hallId,
                startsAt,
                endsAt,
                basePrice,
                ScreeningStatus.SCHEDULED
        );
    }
}
