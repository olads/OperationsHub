package com.migia.OperationsHub;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class OperationsHubApplication {

	public static void main(String[] args) {
		SpringApplication.run(OperationsHubApplication.class, args);
	}

}
