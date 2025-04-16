package net.blueshell.eurekaserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;
import org.springframework.context.annotation.ComponentScan;

@EnableEurekaServer
@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.eurekaserver", "net.blueshell.common"})
public class EurekaServerApplication {
	public static void main(String[] args) {
		System.setProperty("server.servlet.context-path", "/api");
		SpringApplication.run(EurekaServerApplication.class, args);
	}
}
