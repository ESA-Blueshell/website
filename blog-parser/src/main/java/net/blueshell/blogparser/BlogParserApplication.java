package net.blueshell.blogparser;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"net.blueshell.blogparser", "net.blueshell.common"})
public class BlogParserApplication {

    public static void main(String[] args) {
        SpringApplication.run(BlogParserApplication.class, args);
    }
}
