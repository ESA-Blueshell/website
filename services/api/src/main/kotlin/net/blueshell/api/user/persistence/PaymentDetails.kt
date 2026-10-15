package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity

/**
 * The mandate a person is collected under, kept across their memberships. They pay by direct debit
 * exactly while it stands; a person without a row, or whose mandate ended, pays by transfer.
 */
@Entity
@Table(name = "payment_details")
class PaymentDetails(
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: Long,
) : AutoIdEntity() {
    /** The mandate they are collected under, or null where none is recorded. */
    @Embedded
    var mandate: IncassoMandate? = null

    /** Their one bank account, or null where none is on file or it was wiped. */
    @Embedded
    var bankAccount: BankAccount? = null

    /** Whether the mandate was given for an account that is no longer on file, wiped or replaced. */
    val mandateAccountGone: Boolean
        get() = mandate?.accountVersion != null && !mandateAccountKept

    /** Whether the bank account the mandate was given for is still the one on file. */
    val mandateAccountKept: Boolean
        get() = mandate?.accountVersion?.let { it == bankAccount?.version } == true
}
