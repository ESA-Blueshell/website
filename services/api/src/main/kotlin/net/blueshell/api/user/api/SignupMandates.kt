package net.blueshell.api.user.api

import net.blueshell.api.user.domain.IncassoStanding
import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.domain.OnlineAuthorisation
import org.springframework.stereotype.Service
import java.time.LocalDate

/** A person's own mandate as they see it: on their membership, or waiting for it to start. */
data class OwnMandate(
    val standing: IncassoStanding,
    val iban: MaskedIban?,
    val reference: String?,
    val signedOn: LocalDate?,
    /** Set up before the membership started, and moved onto it once it does. */
    val pending: Boolean,
)

/** The signup's optional incasso step, for the module that resolves a signup to its account. */
@Service
class SignupMandates(
    private val mandates: Mandates,
) {
    /** Authorised under [wordingVersion], with [address] the address the signup has just taken. */
    fun setUp(
        userId: Long,
        iban: String,
        accountHolder: String,
        wordingVersion: String,
        address: AddressFields,
    ): OwnMandate = mandates.setUpOwn(userId, iban, accountHolder, OnlineAuthorisation(wordingVersion, address))
}
