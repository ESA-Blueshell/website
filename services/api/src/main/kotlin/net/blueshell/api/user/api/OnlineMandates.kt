package net.blueshell.api.user.api

import net.blueshell.api.user.domain.Mandates
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate

/** An online mandate opened in full, for the one document that prints it. */
data class OpenedOnlineMandate(
    val userId: Long,
    val username: String,
    val reference: String,
    val signedOn: LocalDate,
    val authorisedAt: Instant,
    val wordingVersion: String,
    val wording: String,
    val accountHolder: String,
    val iban: String,
    val address: AddressFields,
) {
    // Nothing of the account or the address reaches a log line.
    override fun toString(): String = "OpenedOnlineMandate($reference)"
}

/** Opens a membership's online mandate, for its PDF and for nothing that answers a member. */
@Service
class OnlineMandates(
    private val mandates: Mandates,
) {
    fun open(membershipId: Long): OpenedOnlineMandate = mandates.openOnline(membershipId)
}
