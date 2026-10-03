package net.blueshell.api.user.api

/** A member changed their own bank details for incasso; [iban] is the new account, masked. */
data class BankDetailsChanged(
    val userId: Long,
    val iban: MaskedIban,
)
