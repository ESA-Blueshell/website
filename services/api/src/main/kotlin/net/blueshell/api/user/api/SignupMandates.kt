package net.blueshell.api.user.api

import net.blueshell.api.user.domain.IncassoStanding
import net.blueshell.api.user.domain.Mandates
import org.springframework.stereotype.Service
import java.time.LocalDate

/** A person's own mandate as they see it: on their membership, or waiting for it to start. */
data class OwnMandate(
    val standing: IncassoStanding,
    val ibanLastFour: String?,
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
    fun setUp(
        userId: Long,
        iban: String,
        accountHolder: String,
    ): OwnMandate = mandates.setUpOwn(userId, iban, accountHolder)
}
