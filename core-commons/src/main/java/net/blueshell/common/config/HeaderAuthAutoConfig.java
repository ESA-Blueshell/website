package net.blueshell.common.config;

import net.blueshell.common.filter.HeaderAuthFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * When you add `net.blueshell:core-commons` to your service,
 * this configuration will register the HeaderAuthenticationFilter
 * on every request, unless you explicitly disable it.
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
@ConditionalOnProperty(prefix = "blueshell.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class HeaderAuthAutoConfig {

    @Bean
    public FilterRegistrationBean<HeaderAuthFilter> headerAuthenticationFilter() {
        FilterRegistrationBean<HeaderAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new HeaderAuthFilter());
        reg.addUrlPatterns("/*");
        return reg;
    }
}

