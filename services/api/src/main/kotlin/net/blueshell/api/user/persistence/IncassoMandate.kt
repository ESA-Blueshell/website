package net.blueshell.api.user.persistence

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant
import java.time.LocalDate

/** How a mandate was signed: by the member on the site, or on paper and recorded by the board. */
@Schema(enumAsRef = true)
enum class MandateKind {
    ONLINE,
    PAPER,
}

/**
 * The bank details and mandate a membership is collected under. The IBAN and account holder are
 * sealed by Vault Transit to the member (api ADR-038); only the masked IBAN is kept readable.
 */
@Embeddable
class IncassoMandate(
    /** Null once wiped, 13 months after the last collection: the rest of the mandate stays as the record. */
    @Column(name = "mandate_iban", length = 255)
    var sealedIban: String?,
    @Column(name = "mandate_account_holder", length = 512)
    var sealedAccountHolder: String?,
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
    @Enumerated(EnumType.STRING)
    @Column(name = "mandate_kind", length = 16)
    var kind: MandateKind = MandateKind.PAPER,
    /** The moment an online mandate was authorised; a paper one has only the date it was signed on. */
    @Column(name = "mandate_authorised_at")
    var authorisedAt: Instant? = null,
    /** Which wording the member agreed to online; see `MandateWording`. */
    @Column(name = "mandate_wording_version", length = 16)
    var wordingVersion: String? = null,
    /** The account that authorised it online. */
    @Column(name = "mandate_authorised_by")
    var authorisedBy: Long? = null,
    /** The address the member confirmed when authorising online, sealed: the mandate's own record, not their account's address. */
    @Column(name = "mandate_address", columnDefinition = "TEXT")
    var sealedAddress: String? = null,
) {
    /** Whether its sealed bank details are gone, which leaves nothing to collect from or to reveal. */
    val wiped: Boolean
        get() = sealedIban == null

    // Nothing of the account reaches a log line.
    override fun toString(): String = "IncassoMandate($ibanMasked, $reference)"
}
