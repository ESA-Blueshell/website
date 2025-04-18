package net.blueshell.socialmediaservice;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@OpenAPIDefinition(
		servers = {
				@Server(url = "${app.url}/social-media")
		}
)

@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.socialmediaservice", "net.blueshell.common"})
public class SocialMediaServiceApplication {

	public static void main(String[] args) {
		System.setProperty("server.servlet.context-path", "/social-media");
		SpringApplication.run(SocialMediaServiceApplication.class, args);
	}

}
