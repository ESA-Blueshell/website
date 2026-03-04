package net.blueshell.api.platform.integration.email.persistence.repository

import net.blueshell.api.platform.integration.email.persistence.EmailSuppression
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface EmailSuppressionRepository : JpaRepository<EmailSuppression, Long> {
    fun findByEmail(email: String): EmailSuppression?
    fun existsByEmail(email: String): Boolean
    fun countByLastSeenAtAfter(since: Instant): Long
}
