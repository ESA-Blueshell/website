package net.blueshell.api.platform.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.factory.PasswordEncoderFactories
import org.springframework.security.crypto.password.DelegatingPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class EncoderConfig {
    @Bean
    fun passwordEncoder(): PasswordEncoder =
        (PasswordEncoderFactories.createDelegatingPasswordEncoder() as DelegatingPasswordEncoder).apply {
            // User passwords are stored as raw BCrypt hashes without an "{id}" prefix,
            // while OAuth client secrets registered with Spring Authorization Server
            // use prefixes like "{noop}<vault-secret>" — DelegatingPasswordEncoder
            // needs both to interoperate.
            setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder())
        }
}
