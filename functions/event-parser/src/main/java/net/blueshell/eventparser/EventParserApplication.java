package net.blueshell.eventparser;

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
