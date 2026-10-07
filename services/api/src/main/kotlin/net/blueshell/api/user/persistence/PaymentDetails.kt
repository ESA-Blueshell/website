package net.blueshell.api.user.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity

/**
 * How a person pays and the mandate they are collected under, kept across their memberships. A
 * person without a row pays by transfer and has no mandate.
 */
@Entity
@Table(name = "payment_details")
class PaymentDetails(
    @Column(name = "user_id", nullable = false, unique = true, updatable = false)
    val userId: Long,
    /** Paid by incasso rather than by transfer; a run collects only where a mandate is recorded too. */
    @Column(name = "incasso", nullable = false)
    var incasso: Boolean = false,
) : AutoIdEntity() {
    /** The bank details and mandate they are collected under, or null where none is recorded. */
    @Embedded
    var mandate: IncassoMandate? = null
}
