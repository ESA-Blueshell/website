package net.blueshell.api.platform.config

import com.github.javafaker.Faker
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.*

/**
 * Test bean configuration for repeatable random data.
 */
@Configuration
class FactoryConfig {

    @Bean
    fun faker(): Faker = Faker(Locale.ENGLISH)

    @Bean
    fun random(): Random = Random()
}
