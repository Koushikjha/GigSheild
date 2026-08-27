package com.gigshield;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GigshieldBackendApplication {
	public static void main(String[] args) {
		SpringApplication.run(GigshieldBackendApplication.class, args);
	}
}