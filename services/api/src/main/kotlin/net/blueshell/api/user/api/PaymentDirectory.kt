package net.blueshell.api.user.api

import net.blueshell.api.user.domain.Mandates
import net.blueshell.api.user.persistence.IncassoMandate
import net.blueshell.api.user.persistence.PaymentDetailsRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** How one person pays, as the contribution module reads it. */
data class PersonPayment(
    /** Pays by direct debit: a mandate stands, with or without bank details to collect from. */
    val incasso: Boolean,
    val mandate: IncassoMandate?,
) {
    /** The mandate they can be collected under: a standing one that still holds its bank details. */
    val collectable: IncassoMandate?
        get() = mandate?.takeIf { incasso && !it.wiped }
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
        return userIds.associateWith { userId ->
            found[userId]?.let { PersonPayment(mandates.stands(it.mandate), it.mandate) } ?: BY_TRANSFER
        }
    }

    /** Keeps a paper mandate without bank details for [userId], as the dev fixtures seed one. */
    fun payByDirectDebit(userId: Long) {
        mandates.recordWithoutBankDetails(userId, recordedBy = null)
    }

    private companion object {
        val BY_TRANSFER = PersonPayment(incasso = false, mandate = null)
    }
}
