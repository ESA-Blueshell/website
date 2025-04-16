package net.blueshell.emailparser;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@OpenAPIDefinition(
		servers = {
				@Server(url = "http://localhost:1024/emailparser")
		}
)

@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.emailparser", "net.blueshell.common"})
public class EmailParserApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmailParserApplication.class, args);
	}
}
