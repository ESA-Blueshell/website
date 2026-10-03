package net.blueshell.api.user.domain

import net.blueshell.api.user.domain.sealing.Sealed
import net.blueshell.api.user.domain.sealing.SealedValueUnopenable
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.domain.sealing.sealingContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** A mandate's IBAN and account holder as they are stored, each sealed. */
data class SealedAccount(
    val iban: String,
    val accountHolder: String,
)

/** A sealed account and the member it is bound to, which opening it needs. */
data class HeldAccount(
    val userId: Long,
    val sealed: SealedAccount,
)

/**
 * A mandate's IBAN and account holder, sealed through Vault Transit under `privacy.bank-details-key`
 * (api ADR-038). Each is bound to its field and the member, so a sealed IBAN copied onto another
 * member's mandate does not open there and cannot redirect a collection. The two are sealed in
 * one call, so they share a key version. Nothing falls back to plaintext: with the key out of
 * reach every write and the batch file answer [SealingUnavailable].
 */
@Service
class SealedBankDetails(
    private val sealer: Sealer,
    @param:Value($$"${privacy.bank-details-key:api-bank-details}") val key: String,
) {
    fun seal(
        userId: Long,
        iban: Iban,
        accountHolder: String,
    ): SealedAccount {
        val (sealedIban, sealedHolder) =
            sealer.seal(
                key,
                listOf(Sealed(iban.value, sealingContext(IBAN, userId)), Sealed(accountHolder, sealingContext(ACCOUNT_HOLDER, userId))),
            )
        return SealedAccount(sealedIban, sealedHolder)
    }

    /** Opens every account in one call. One that does not open for its member refuses them all with [BankDetailsUnopenable]. */
    fun open(accounts: List<HeldAccount>): List<BankDetails> {
        val values =
            accounts.flatMap {
                listOf(
                    Sealed(it.sealed.iban, sealingContext(IBAN, it.userId)),
                    Sealed(it.sealed.accountHolder, sealingContext(ACCOUNT_HOLDER, it.userId)),
                )
            }
        val opened =
            try {
                sealer.open(key, values)
            } catch (refused: SealedValueUnopenable) {
                throw BankDetailsUnopenable(refused)
            }
        return opened.chunked(2).map { (iban, holder) -> BankDetails(Iban.parse(iban) ?: throw BankDetailsUnopenable(null), holder) }
    }

    /** The account holder for the mandate panel, or null where it cannot be opened now: the panel then shows the masked IBAN alone. */
    fun accountHolder(account: HeldAccount): String? =
        try {
            sealer.open(key, listOf(Sealed(account.sealed.accountHolder, sealingContext(ACCOUNT_HOLDER, account.userId)))).single()
        } catch (away: SealingUnavailable) {
            log.warn("[privacy] the account holder of user {} cannot be opened now: {}", account.userId, away.message)
            null
        } catch (refused: SealedValueUnopenable) {
            log.warn("[privacy] the account holder of user {} does not open under its member", account.userId, refused)
            null
        }

    companion object {
        const val IBAN = "iban"
        const val ACCOUNT_HOLDER = "account-holder"
        private val log = LoggerFactory.getLogger(SealedBankDetails::class.java)
    }
}
