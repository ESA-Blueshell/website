package net.blueshell.fileservice;

import net.blueshell.fileservice.config.StorageConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageConfig.class)
public class FileServiceApplication {

	public static void main(String[] args) {
		System.setProperty("server.servlet.context-path", "/files");
		SpringApplication.run(FileServiceApplication.class, args);
	}

}
