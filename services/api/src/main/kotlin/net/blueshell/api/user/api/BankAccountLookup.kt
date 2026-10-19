package net.blueshell.api.user.api

import net.blueshell.api.user.domain.BankAccounts
import org.springframework.stereotype.Service

/** Whether a person has a bank account on file, which a declaration is paid to (api ADR-041). */
@Service
class BankAccountLookup(
    private val accounts: BankAccounts,
) {
    fun isOnFile(userId: Long): Boolean = accounts.own(userId) != null
}
