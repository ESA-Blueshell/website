package net.blueshell.api.user.api

import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.persistence.IncassoMandate
import org.springframework.stereotype.Service

/** An account a mandate collects from, in full. */
data class CollectionAccount(
    val iban: String,
    val accountHolder: String,
) {
    // Nothing of the account reaches a log line.
    override fun toString(): String = "CollectionAccount(****${iban.takeLast(SHOWN)})"

    private companion object {
        const val SHOWN = 4
    }
}

/** Opens a mandate's sealed account, for ING's batch file and for nothing that answers a browser. */
@Service
class CollectionAccounts(
    private val mandates: Mandates,
) {
    fun of(mandate: IncassoMandate): CollectionAccount =
        mandates.bankDetailsOf(mandate).let { CollectionAccount(it.iban.value, it.accountHolder) }
}
