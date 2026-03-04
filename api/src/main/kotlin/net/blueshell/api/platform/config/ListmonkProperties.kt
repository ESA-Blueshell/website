package net.blueshell.api.platform.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "listmonk")
data class ListmonkProperties(
    val api: ApiProperties = ApiProperties(),
    val from: FromProperties = FromProperties(),
    val replyTo: String = "sitecie@blueshell.utwente.nl",
    val templateId: Int = 0,  // 0 = auto-detect/create on startup
    val bounce: BounceProperties = BounceProperties(),
    val contact: ContactProperties = ContactProperties(),
) {
    data class ApiProperties(
        val baseUrl: String = "http://listmonk:9000/api",
        val username: String = "listmonk",
        val password: String = "listmonk",
        /** Username for the API user created via LISTMONK_ADMIN_API_USER during --install. */
        val apiUser: String = "api",
        /** Path to file written by the listmonk install step containing the API token (optional). */
        val tokenFile: String = "/run/secrets/listmonk/api-token.env",
    )

    data class FromProperties(
        val address: String = "no-reply@mg.esa-blueshell.nl",
    )

    data class BounceProperties(
        val pollIntervalMs: Long = 300_000L,
        val mailbox: MailboxProperties = MailboxProperties(),
    ) {
        data class MailboxProperties(
            /** Enable Listmonk bounce mailbox polling (IMAP). */
            val enabled: Boolean = false,
            val host: String = "",
            val port: Int = 993,
            val username: String = "",
            val password: String = "",
            val tlsEnabled: Boolean = true,
            val tlsSkipVerify: Boolean = false,
            val folder: String = "INBOX",
            val returnPath: String = "",
            val scanInterval: String = "10m",
        )
    }

    data class ContactProperties(
        /** Cron expression for the daily contact sync job. */
        val syncCron: String = "0 0 2 * * *",
    )
}
