package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.email.bounce-imap")
data class ImapBounceProperties(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 993,
    val username: String = "",
    val password: String = "",
    val folder: String = "INBOX",
    val ssl: Boolean = true,
    val batchSize: Int = 50,
    val connectionTimeoutMs: Int = 10000,
    val readTimeoutMs: Int = 30000,
)
