package net.blueshell.eventparser;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.eventparser", "net.blueshell.common"})
public class EventParserApplication {
	public static void main(String[] args) {
		SpringApplication.run(EventParserApplication.class, args);
	}
}
