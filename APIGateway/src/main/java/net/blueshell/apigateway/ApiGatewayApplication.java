package net.blueshell.apigateway;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@OpenAPIDefinition(
		servers = {@Server(url= "/")}
)

@SpringBootApplication
@EnableAsync
public class ApiGatewayApplication {
	public static void main(String[] args) {
		System.setProperty("server.servlet.context-path", "/apiGateway");
		SpringApplication.run(ApiGatewayApplication.class, args);
	}
}
