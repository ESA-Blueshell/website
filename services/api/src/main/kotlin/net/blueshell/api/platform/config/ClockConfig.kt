package net.blueshell.api.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import java.time.Clock

@Configuration
class ClockConfig {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    @Primary
    @Profile("test")
    fun settableClock(): SettableClock = SettableClock()
}
