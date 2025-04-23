package net.blueshell.db.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.util.List;

public class DbEnvPostProcessor implements EnvironmentPostProcessor {
    private static final String[] CONFIG_FILES = {
            "application-db.yaml"
    };
    private final YamlPropertySourceLoader loader = new YamlPropertySourceLoader();

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment,
            SpringApplication application
    ) {
        for (String configFile : CONFIG_FILES) {
            Resource resource = new ClassPathResource(configFile);
            if (resource.exists()) {
                try {
                    // Load YAML file and add as property sources
                    List<PropertySource<?>> sources = loader.load(configFile, resource);
                    // Add sources with LOWEST precedence (microservice's configs override these)
                    for (PropertySource<?> source : sources) {
                        environment.getPropertySources().addLast(source);
                    }
                } catch (IOException e) {
                    throw new RuntimeException("Failed to load YAML config: " + configFile, e);
                }
            }
        }
    }
}