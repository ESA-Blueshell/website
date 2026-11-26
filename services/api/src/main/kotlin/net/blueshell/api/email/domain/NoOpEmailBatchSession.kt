package net.blueshell.api.email.domain

import net.blueshell.api.email.api.EmailBatchSession
import net.blueshell.api.shared.credentials.Credentials
import net.blueshell.api.shared.credentials.WhenCredentialsMissing
import org.springframework.stereotype.Component

/** Batch scope where no SMTP relay is configured: each in-memory send stands on its own. */
@Component
@WhenCredentialsMissing(Credentials.SMTP_HOST)
class NoOpEmailBatchSession : EmailBatchSession {
    override fun <T> open(block: () -> T): T = block()
}
