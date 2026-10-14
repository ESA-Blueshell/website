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

/** Opens mandates' sealed accounts, for ING's batch file and for nothing that answers a browser. */
@Service
class CollectionAccounts(
    private val mandates: Mandates,
) {
    /** Each member's account, in the order given, opened in one call. */
    fun of(held: List<Pair<Long, IncassoMandate>>): List<CollectionAccount> =
        mandates.bankDetailsOf(held).map { CollectionAccount(it.iban.value, it.accountHolder) }
}
