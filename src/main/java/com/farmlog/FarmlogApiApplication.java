package com.farmlog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FarmlogApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(FarmlogApiApplication.class, args);
	}

}
