package net.blueshell.api.user.api

import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** How one person pays, as the contribution module reads it. */
data class PersonPayment(
    val incasso: Boolean,
    val mandate: IncassoMandate?,
) {
    /** The mandate they can be collected under: one whose bank details were wiped is a record only. */
    val collectable: IncassoMandate?
        get() = mandate?.takeUnless { it.wiped }
}

/** How people pay, read for the contribution reminders, notifications and incasso runs. */
@Service
class PaymentDirectory(
    private val payments: PaymentDetailsRepository,
    private val mandates: Mandates,
) {
    /** Each person's way of paying and mandate; somebody with none recorded pays by transfer. */
    @Transactional(readOnly = true)
    fun of(userIds: Collection<Long>): Map<Long, PersonPayment> {
        if (userIds.isEmpty()) return emptyMap()
        val found = payments.findByUserIdIn(userIds.toSet()).associateBy { it.userId }
        return userIds.associateWith { userId -> found[userId]?.let { PersonPayment(it.incasso, it.mandate) } ?: BY_TRANSFER }
    }

    /** Sets whether [userId] pays by incasso or by transfer, as the board's Pays by does. */
    fun payBy(
        userId: Long,
        incasso: Boolean,
    ) {
        mandates.payBy(userId, incasso)
    }

    private companion object {
        val BY_TRANSFER = PersonPayment(incasso = false, mandate = null)
    }
}
