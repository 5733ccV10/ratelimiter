package com.carrera.ratelimiter;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class RatelimiterApplication {

	public static void main(String[] args) {
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(RatelimiterApplication.class, args);
	}

}
