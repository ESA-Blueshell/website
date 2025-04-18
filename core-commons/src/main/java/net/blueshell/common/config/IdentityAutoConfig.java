package net.blueshell.common.config;

import lombok.extern.slf4j.Slf4j;
import net.blueshell.common.identity.IdentityFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * When you add `net.blueshell:core-commons` to your service,
 * this configuration will register the IdentityFilter
 * on every request, unless you explicitly disable it.
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.web.filter.OncePerRequestFilter")
@ConditionalOnProperty(prefix = "identity.provider", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IdentityAutoConfig {
    @Bean
    public FilterRegistrationBean<IdentityFilter> IdentityFilter() {
        FilterRegistrationBean<IdentityFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(new IdentityFilter());
        reg.addUrlPatterns("/*");
        return reg;
    }
}