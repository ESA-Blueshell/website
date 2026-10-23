package net.blueshell.api.user.api

import net.blueshell.api.user.domain.BankAccounts
import org.springframework.stereotype.Service
import java.time.LocalDate

/** A bank account as the board pays to it: the holder opened, the IBAN masked, since only a reveal opens it. */
data class BankAccountToPay(
    val accountHolder: String?,
    val iban: MaskedIban,
    /** Named only for a bank outside the Netherlands. */
    val bic: String?,
) {
    override fun toString(): String = "BankAccountToPay($iban)"
}

/** The bank account a declaration is paid to (api ADR-041). */
@Service
class BankAccountLookup(
    private val accounts: BankAccounts,
) {
    fun isOnFile(userId: Long): Boolean = accounts.own(userId) != null

    fun toPay(userId: Long): BankAccountToPay? = accounts.toPay(userId)

    /** Counts a payout on [on] as a use of the account on file, and answers its preview; null where none is on file. */
    fun recordPaidTo(
        userId: Long,
        on: LocalDate,
    ): MaskedIban? = accounts.recordPaidTo(userId, on)
}
