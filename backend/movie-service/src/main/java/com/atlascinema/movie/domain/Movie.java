package com.atlascinema.movie.domain;

import com.atlascinema.movie.exception.MovieAlreadyActiveException;
import com.atlascinema.movie.exception.MovieAlreadyDiscontinuedException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "movies")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Movie {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "age_rating", nullable = false)
    private AgeRating ageRating;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Genre genre;

    @Column(name = "release_date", nullable = false)
    private LocalDate releaseDate;

    @Column(name = "poster_url")
    private String posterUrl;

    @Column(name = "trailer_url")
    private String trailerUrl;

    @Column(nullable = false)
    private boolean discontinued;

    private Movie(
            UUID id,
            String title,
            String description,
            Integer durationMinutes,
            AgeRating ageRating,
            Genre genre,
            LocalDate releaseDate,
            String posterUrl,
            String trailerUrl,
            boolean discontinued
    ) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.ageRating = ageRating;
        this.genre = genre;
        this.releaseDate = releaseDate;
        this.posterUrl = posterUrl;
        this.trailerUrl = trailerUrl;
        this.discontinued = discontinued;
    }

    public static Movie create(
            String title,
            String description,
            Integer durationMinutes,
            AgeRating ageRating,
            Genre genre,
            LocalDate releaseDate,
            String posterUrl,
            String trailerUrl
    ) {
        return new Movie(
                UUID.randomUUID(),
                title,
                description,
                durationMinutes,
                ageRating,
                genre,
                releaseDate,
                posterUrl,
                trailerUrl,
                false
        );
    }

    public void updateDetails(
            String title,
            String description,
            Integer durationMinutes,
            AgeRating ageRating,
            Genre genre,
            LocalDate releaseDate,
            String posterUrl,
            String trailerUrl
    ){
        this.title = title;
        this.description = description;
        this.durationMinutes = durationMinutes;
        this.ageRating = ageRating;
        this.genre = genre;
        this.releaseDate = releaseDate;
        this.posterUrl = posterUrl;
        this.trailerUrl = trailerUrl;
    }

    public void discontinue() {
        if (discontinued) {
            throw new MovieAlreadyDiscontinuedException(id);
        }
        this.discontinued = true;
    }

    public void restore(){
        if(!discontinued){
            throw new MovieAlreadyActiveException(id);
        }
        this.discontinued = false;
    }
}
