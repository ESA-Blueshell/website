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
            // Fallback so existing prefix-less BCrypt user-password hashes keep matching.
            setDefaultPasswordEncoderForMatches(BCryptPasswordEncoder())
        }
}
