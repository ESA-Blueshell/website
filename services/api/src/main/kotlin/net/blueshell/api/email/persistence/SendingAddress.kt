package net.blueshell.api.email.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

/** How a connection to a mail server, SMTP or IMAP, is secured. */
@Schema(enumAsRef = true)
enum class MailSecurity {
    /** Plain, then upgraded with STARTTLS: usually port 587 for SMTP, 143 for IMAP. */
    STARTTLS,

    /** TLS from the first byte: usually port 465 for SMTP, 993 for IMAP. */
    SSL,

    /** Neither; for a server on the same private network only. */
    NONE,
}

/**
 * An address the site sends from and reads. Its one login, for SMTP and IMAP alike, is kept in
 * Vault (api ADR-039); the default address sends the site's own mail.
 */
@Entity
@Table(name = "sending_address")
class SendingAddress(
    @Column(name = "address", nullable = false, length = 320)
    var address: String,
    @Column(name = "display_name", nullable = false, length = 128)
    var displayName: String,
    @Column(name = "host", nullable = false)
    var host: String,
    @Column(name = "port", nullable = false)
    var port: Int,
    @Enumerated(EnumType.STRING)
    @Column(name = "security", nullable = false, length = 16)
    var security: MailSecurity,
    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
    /** When its login was last written to Vault; null while none is kept. */
    @Column(name = "login_kept_at")
    var loginKeptAt: Instant? = null,
    /** Its IMAP server; null where the address is not read. */
    @Column(name = "imap_host")
    var imapHost: String? = null,
    @Column(name = "imap_port")
    var imapPort: Int? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "imap_security", length = 16)
    var imapSecurity: MailSecurity? = null,
) : AutoIdEntity() {
    /** What the last check found: whether it sends, whether it is read, and why not. Null until checked. */
    @Column(name = "can_send")
    var canSend: Boolean? = null

    @Column(name = "can_read")
    var canRead: Boolean? = null

    @Column(name = "send_failure", length = 300)
    var sendFailure: String? = null

    @Column(name = "read_failure", length = 300)
    var readFailure: String? = null

    @Column(name = "checked_at")
    var checkedAt: Instant? = null
}

interface SendingAddressRepository : JpaRepository<SendingAddress, Long> {
    fun findAllByOrderByAddress(): List<SendingAddress>

    fun existsByAddressIgnoreCase(address: String): Boolean

    fun findFirstByIsDefaultTrue(): SendingAddress?
}
