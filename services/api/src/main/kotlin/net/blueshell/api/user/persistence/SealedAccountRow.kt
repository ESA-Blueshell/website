package net.blueshell.api.user.persistence

/** A row's sealed IBAN and account holder with the member they are bound to, read past soft deletion. */
interface SealedAccountRow {
    val id: Long
    val userId: Long
    val iban: String
    val accountHolder: String

    /** An online mandate's sealed address, which a paper mandate has none of. */
    val address: String?
}
