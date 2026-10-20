package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant
import java.time.LocalDate

/**
 * A person's one bank account, kept apart from the mandate given for it (api ADR-041). The IBAN and
 * account holder are sealed by Vault Transit to the person (ADR-038); the masked IBAN stays readable.
 */
@Embeddable
class BankAccount(
    @Column(name = "account_iban", length = 255)
    var sealedIban: String,
    @Column(name = "account_holder", length = 512)
    var sealedAccountHolder: String,
    /** Named only for a bank outside the Netherlands. */
    @Column(name = "account_bic", length = 11)
    var bic: String?,
    /** The IBAN's country code and last two characters, as `NL34`; see `MaskedIban`. */
    @Column(name = "account_iban_masked", length = 4)
    var ibanMasked: String,
    /** Counts up each time the account is replaced, so a mandate can say which one it was given for. */
    @Column(name = "account_version")
    var version: Int,
    @Column(name = "account_recorded_at")
    var recordedAt: Instant,
) {
    /** The last day a declaration was paid to it; a new account starts without one (api ADR-041). */
    @Column(name = "account_last_paid_to_on")
    var lastPaidToOn: LocalDate? = null

    // Nothing of the account reaches a log line.
    override fun toString(): String = "BankAccount($ibanMasked, v$version)"
}
