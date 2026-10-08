package io.github.kwp6zgnrsh.boardkitapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class BoardKitApiApplication {

	static void main(String[] args) {
		SpringApplication.run(BoardKitApiApplication.class, args);
	}

}
