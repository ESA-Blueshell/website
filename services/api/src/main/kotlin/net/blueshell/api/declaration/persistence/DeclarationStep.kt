package net.blueshell.api.declaration.persistence

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import java.time.Instant

/** One change of a declaration's state: who made it, when, and the reason where one is given. */
@Embeddable
data class DeclarationStep(
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 16)
    val state: DeclarationState,
    @Column(name = "at", nullable = false)
    val at: Instant,
    @Column(name = "by_user_id")
    val byUserId: Long?,
    @Column(name = "reason", length = REASON_MAX)
    val reason: String? = null,
    /** Kept as `MaskedIban.stored`, since the account on file may change after it was paid. */
    @Column(name = "paid_to", length = 4)
    val paidTo: String? = null,
) {
    companion object {
        const val REASON_MAX = 1000
    }
}
