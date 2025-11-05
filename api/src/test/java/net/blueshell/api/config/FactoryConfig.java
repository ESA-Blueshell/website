package net.blueshell.api.config;

import com.github.javafaker.Faker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Random;

/**
 * Test bean configuration for repeatable random data.
 */
@Configuration
public class FactoryConfig {

    @Bean
    public Faker faker() {
        return new Faker(Locale.ENGLISH);
    }

    @Bean
    public Random random() {
        return new Random();
    }
}
