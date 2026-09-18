package net.blueshell.api.platform.config

import net.blueshell.api.shared.hibernate.DirtyTrackingInterceptor
import org.hibernate.Interceptor
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the interceptor at the SessionFactory level.
 */
@Configuration
class HibernateDirtyTrackingConfig {
    @Bean
    fun dirtyTrackingInterceptor(): Interceptor = DirtyTrackingInterceptor()

    @Bean
    fun dirtyTrackingCustomizer(dirtyTrackingInterceptor: Interceptor): HibernatePropertiesCustomizer =
        HibernatePropertiesCustomizer { props: MutableMap<String, Any> ->
            props["hibernate.session_factory.interceptor"] = dirtyTrackingInterceptor
        }
}
