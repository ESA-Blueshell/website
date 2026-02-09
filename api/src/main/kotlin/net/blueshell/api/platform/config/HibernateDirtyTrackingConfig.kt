package net.blueshell.api.platform.config

import net.blueshell.api.shared.model.hibernate.DirtyTrackingInterceptor
import org.hibernate.Interceptor
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Registers the interceptor at the SessionFactory level.
 */
@Configuration
class HibernateDirtyTrackingConfig {
    @Bean
    fun dirtyTrackingInterceptor(): Interceptor {
        return DirtyTrackingInterceptor()
    }

    @Bean
    fun dirtyTrackingCustomizer(dirtyTrackingInterceptor: Interceptor?): HibernatePropertiesCustomizer {
        return HibernatePropertiesCustomizer { props: MutableMap<String?, Any?>? ->
            props!!["hibernate.session_factory.interceptor"] = dirtyTrackingInterceptor
        }
    }
}
