package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "security.cors")
data class SecurityCorsProperties(
    val allowedOrigins: List<String> = emptyList()
)
