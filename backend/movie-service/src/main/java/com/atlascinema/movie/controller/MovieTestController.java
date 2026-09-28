package com.atlascinema.movie.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MovieTestController {

    @GetMapping("/test")
    public String test(){
        return "movie-service works";
    }
}
