package com.atlascinema.screening;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients
@SpringBootApplication
public class ScreeningServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScreeningServiceApplication.class, args);
	}

}
