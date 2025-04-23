package net.blueshell.common.identity;

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
@ConditionalOnProperty(prefix = "blueshell.identity", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdentityFilterAutoConfig {

    @Bean
    public FilterRegistrationBean<IdentityFilter> headerAuthenticationFilter() {
        FilterRegistrationBean<IdentityFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdentityFilter());
        reg.addUrlPatterns("/*");
        return reg;
    }
}

