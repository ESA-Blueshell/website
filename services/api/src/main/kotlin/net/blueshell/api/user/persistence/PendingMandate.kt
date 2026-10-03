package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant
import java.time.LocalDate

/**
 * Bank details an applicant set up before their membership started, sealed until it does. Always an
 * online mandate: it carries when and under which wording it was authorised, and the address confirmed.
 */
@Entity
@Table(name = "pending_mandates")
class PendingMandate(
    @Column(name = "user_id", nullable = false, unique = true)
    val userId: Long,
    @Column(name = "iban", nullable = false, length = 255)
    var sealedIban: String,
    @Column(name = "account_holder", nullable = false, length = 512)
    var sealedAccountHolder: String,
    /** The IBAN's country code and last two characters, as `NL34`; see `MaskedIban`. */
    @Column(name = "iban_masked", nullable = false, length = 4)
    var ibanMasked: String,
    @Column(name = "signed_on", nullable = false)
    var signedOn: LocalDate,
    @Column(name = "authorised_at", nullable = false)
    var authorisedAt: Instant,
    @Column(name = "wording_version", nullable = false, length = 16)
    var wordingVersion: String,
    @Column(name = "address", nullable = false, length = 1024)
    var sealedAddress: String,
) : AutoIdEntity() {
    // Nothing of the account reaches a log line.
    override fun toString(): String = "PendingMandate($ibanMasked)"
}
