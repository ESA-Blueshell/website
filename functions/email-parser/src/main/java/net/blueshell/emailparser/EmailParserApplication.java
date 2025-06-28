package net.blueshell.emailparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.emailparser", "net.blueshell.common"})
public class EmailParserApplication {

	public static void main(String[] args) {
		SpringApplication.run(EmailParserApplication.class, args);
	}
}
