CREATE TABLE movies (
id UUID PRIMARY KEY,
title VARCHAR(255) NOT NULL,
description TEXT,
duration_minutes INTEGER NOT NULL,
age_rating VARCHAR(32) NOT NULL,
genre VARCHAR(64) NOT NULL,
release_date DATE NOT NULL,
poster_url VARCHAR(1000),
trailer_url VARCHAR(1000),
discontinued BOOLEAN NOT NULL DEFAULT FALSE
);