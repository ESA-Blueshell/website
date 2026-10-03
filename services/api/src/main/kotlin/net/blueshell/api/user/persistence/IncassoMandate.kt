package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import java.time.Instant
import java.time.LocalDate

/**
 * The bank details and mandate a membership is collected under. The IBAN and account holder are
 * sealed by Vault Transit to the member (api ADR-038); only the masked IBAN is kept readable.
 */
@Embeddable
class IncassoMandate(
    @Column(name = "mandate_iban", length = 255)
    var sealedIban: String,
    @Column(name = "mandate_account_holder", length = 512)
    var sealedAccountHolder: String,
    /** The IBAN's country code and last two characters, as `NL34`; see `MaskedIban`. */
    @Column(name = "mandate_iban_masked", length = 4)
    var ibanMasked: String,
    @Column(name = "mandate_reference", length = 35)
    var reference: String,
    @Column(name = "mandate_signed_on")
    var signedOn: LocalDate,
    @Column(name = "mandate_recorded_by")
    var recordedBy: Long?,
    @Column(name = "mandate_recorded_at")
    var recordedAt: Instant,
) {
    // Nothing of the account reaches a log line.
    override fun toString(): String = "IncassoMandate($ibanMasked, $reference)"
}
