package net.blueshell.blogservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BlogServiceApplication {

    public static void main(String[] args) {
        System.setProperty("server.servlet.context-path", "/blogs");
        SpringApplication.run(BlogServiceApplication.class, args);
    }
}
