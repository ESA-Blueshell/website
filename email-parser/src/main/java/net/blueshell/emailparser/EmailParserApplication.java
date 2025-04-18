package net.blueshell.emailparser;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.emailparser", "net.blueshell.common"})
public class EmailParserApplication {

	public static void main(String[] args) {
		System.setProperty("server.servlet.context-path", "/email-parser");
		SpringApplication.run(EmailParserApplication.class, args);
	}
}
