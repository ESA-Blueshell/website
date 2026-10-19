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
 * The mandate a person is collected under, given for one version of their bank account (api
 * ADR-041). The masked IBAN is the record of the account it was given for.
 */
@Embeddable
class IncassoMandate(
    /** The IBAN's country code and last two characters, as `NL34`, of the account it was given for; null without one. */
    @Column(name = "mandate_iban_masked", length = 4)
    var ibanMasked: String?,
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
    /** The day it stopped: stopped by the member, its bank account replaced or deleted, or their membership ended (api ADR-040). */
    @Column(name = "mandate_ended_on")
    var endedOn: LocalDate? = null,
    /** The version of the person's bank account it was given for; null for a paper mandate without bank details. */
    @Column(name = "mandate_account_version")
    var accountVersion: Int? = null,
) {
    // Nothing of the account reaches a log line.
    override fun toString(): String = "IncassoMandate($ibanMasked, $reference)"
}
