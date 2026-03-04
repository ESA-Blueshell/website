package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "listmonk")
data class ListmonkProperties(
    val api: ApiProperties = ApiProperties(),
    val from: FromProperties = FromProperties(),
    val replyTo: String = "sitecie@blueshell.utwente.nl",
    val templateId: Int = 0,  // 0 = auto-detect/create on startup
    val bounce: BounceProperties = BounceProperties(),
) {
    data class ApiProperties(
        val baseUrl: String = "http://listmonk:9000/api",
        val username: String = "listmonk",
        val password: String = "listmonk",
    )

    data class FromProperties(
        val address: String = "no-reply@mg.esa-blueshell.nl",
    )

    data class BounceProperties(
        val pollIntervalMs: Long = 300_000L,
    )
}
