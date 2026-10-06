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

/** How a connection to an SMTP server is secured. */
@Schema(enumAsRef = true)
enum class SmtpSecurity {
    /** Plain, then upgraded with STARTTLS, usually on port 587. */
    STARTTLS,

    /** TLS from the first byte, usually on port 465. */
    SSL,

    /** Neither; for a server on the same private network only. */
    NONE,
}

/** An address the board may send a written email from. Its SMTP login is kept in Vault (api ADR-039). */
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
    var security: SmtpSecurity,
    @Column(name = "is_default", nullable = false)
    var isDefault: Boolean = false,
    /** When its login was last written to Vault; null while none is kept. */
    @Column(name = "login_kept_at")
    var loginKeptAt: Instant? = null,
) : AutoIdEntity()

interface SendingAddressRepository : JpaRepository<SendingAddress, Long> {
    fun findAllByOrderByAddress(): List<SendingAddress>

    fun existsByAddressIgnoreCase(address: String): Boolean
}
