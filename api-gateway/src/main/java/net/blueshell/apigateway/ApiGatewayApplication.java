package net.blueshell.apigateway;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;

@OpenAPIDefinition(
		servers = {@Server(url= "/")}
)

@SpringBootApplication
@EnableAsync
@EnableFeignClients(basePackages = "net.blueshell.common.client")
@ComponentScan(basePackages = {"net.blueshell.apigateway", "net.blueshell.common"})
public class ApiGatewayApplication {
	public static void main(String[] args) {
		SpringApplication.run(ApiGatewayApplication.class, args);
	}
}
