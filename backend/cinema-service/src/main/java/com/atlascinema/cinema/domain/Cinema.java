package com.atlascinema.cinema.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "cinemas")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Cinema {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 500)
    private String address;

    @Column(nullable = false)
    private boolean active;

    @OneToMany(
            mappedBy = "cinema",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    private List<Hall> halls = new ArrayList<>();

    private Cinema(
            UUID id,
            String name,
            String city,
            String address,
            boolean active
    ) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.address = address;
        this.active = active;
    }

    public static Cinema create(
            String name,
            String city,
            String address
    ) {
        return new Cinema(
                UUID.randomUUID(),
                name,
                city,
                address,
                true
        );
    }

    public Hall addHall(String name) {
        Hall hall = Hall.create(name, this);
        halls.add(hall);

        return hall;
    }
}
