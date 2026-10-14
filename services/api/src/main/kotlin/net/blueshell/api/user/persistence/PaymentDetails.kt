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
    /** The bank details and mandate they are collected under, or null where none is recorded. */
    @Embedded
    var mandate: IncassoMandate? = null
}
