package net.blueshell.telemetry;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// TODO read base url from config file (decouple it from the code)
@OpenAPIDefinition(
		servers = {
				@Server(url = "http://localhost:1024/telemetry")
		}
)

@SpringBootApplication
public class TelemetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryApplication.class, args);
    }

}
