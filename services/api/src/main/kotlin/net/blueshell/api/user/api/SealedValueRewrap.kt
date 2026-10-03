package net.blueshell.api.user.api

import net.blueshell.api.user.domain.sealing.Sealed
import net.blueshell.api.user.domain.sealing.Sealer
import net.blueshell.api.user.domain.sealing.SealingUnavailable
import net.blueshell.api.user.domain.sealing.keyVersionOf
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate

/** What one run moved, and the values it could not move, each named by its field and row. */
data class RewrapReport(
    val moved: Int,
    val failed: List<String>,
)

/**
 * Moves every sealed value that sits below the newest version of its key onto that version. It
 * only asks the sealer to rewrap, so no plaintext reaches the api. A value already on the newest
 * version comes back on the same version and is left as it is, so a second run changes nothing.
 */
@Service
class SealedValueRewrap(
    private val sealer: Sealer,
    private val fields: List<SealedField>,
    transactionManager: PlatformTransactionManager,
) {
    // A transaction of its own per value: a run that ends by reporting a failure keeps what it moved.
    private val transactions =
        TransactionTemplate(transactionManager).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }

    fun rewrapEvery(): RewrapReport {
        val reports = fields.map(::rewrap)
        return RewrapReport(reports.sumOf { it.moved }, reports.flatMap { it.failed })
    }

    private fun rewrap(field: SealedField): RewrapReport {
        var moved = 0
        val failed = mutableListOf<String>()
        field.sealedValues().chunked(BATCH).forEach { batch ->
            batch.zip(rewrapped(field, batch)).forEach { (value, answer) ->
                when {
                    answer == null -> failed += "${field.name} ${value.id}"
                    keyVersionOf(answer) == keyVersionOf(value.sealed) -> Unit
                    transactions.execute { field.swap(value.id, value.sealed, answer) } == true -> moved++
                }
            }
        }
        return RewrapReport(moved, failed)
    }

    // A batch the vault refuses whole fails each of its values, and the batches after it still run.
    private fun rewrapped(
        field: SealedField,
        batch: List<SealedValue>,
    ): List<String?> =
        try {
            sealer.rewrap(field.key, batch.map { Sealed(it.sealed, it.context) })
        } catch (away: SealingUnavailable) {
            log.warn("[privacy] {} {} values could not be rewrapped now: {}", batch.size, field.name, away.message)
            List(batch.size) { null }
        }

    private companion object {
        const val BATCH = 200
        val log = LoggerFactory.getLogger(SealedValueRewrap::class.java)
    }
}
