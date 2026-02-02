package net.blueshell.api.config;

import net.blueshell.api.common.hibernate.DirtyTrackingInterceptor;
import org.hibernate.Interceptor;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Registers the interceptor at the SessionFactory level.
 */
@Configuration
public class HibernateDirtyTrackingConfig {

    @Bean
    public Interceptor dirtyTrackingInterceptor() {
        return new DirtyTrackingInterceptor();
    }

    @Bean
    public HibernatePropertiesCustomizer dirtyTrackingCustomizer(Interceptor dirtyTrackingInterceptor) {
        return (Map<String, Object> props) ->
                props.put("hibernate.session_factory.interceptor", dirtyTrackingInterceptor);
    }
}
