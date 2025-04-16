package net.blueshell.eventparser;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@OpenAPIDefinition(
		servers = {
				@Server(url = "http://localhost:1024/eventparser")
		}
)

@SpringBootApplication
public class EventParserApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventParserApplication.class, args);
	}

}
