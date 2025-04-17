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
		System.setProperty("server.servlet.context-path", "/event-parser");
		SpringApplication.run(EventParserApplication.class, args);
	}
}
