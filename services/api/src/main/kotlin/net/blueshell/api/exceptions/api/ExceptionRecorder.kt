package net.blueshell.api.exceptions.api

import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Clock

/**
 * Records one occurrence of an exception against its fault, creating the fault the first time
 * and reopening it if it was resolved.
 *
 * Recording never throws and never recurses: it runs in its own transaction so a rolled-back
 * caller cannot take it along, a failure while recording is logged and dropped, and an exception
 * raised while this thread is already recording is not recorded again.
 */
@Component
class ExceptionRecorder(
    private val records: RecordedExceptionRepository,
    transactions: PlatformTransactionManager,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(ExceptionRecorder::class.java)
    private val ownTransaction =
        TransactionTemplate(transactions).apply { propagationBehavior = TransactionDefinition.PROPAGATION_REQUIRES_NEW }
    private val recording = ThreadLocal.withInitial { false }

    fun record(
        ex: Throwable,
        concern: ExceptionConcern,
    ) {
        if (recording.get()) return
        recording.set(true)
        try {
            val type = ex.javaClass.name.take(TYPE_LENGTH)
            val thrownAt = thrownAt(ex).take(PLACE_LENGTH)
            ownTransaction.executeWithoutResult {
                records.recordOccurrence(
                    fingerprint = fingerprint(type, thrownAt),
                    exceptionType = type,
                    thrownAt = thrownAt,
                    seenAt = clock.instant(),
                    message = ex.message,
                    stackTrace = ex.stackTraceToString(),
                    source = concern.source.name,
                    concern = concern.label.take(PLACE_LENGTH),
                    jobExecutionId = concern.jobExecutionId,
                )
            }
        } catch (failure: Exception) {
            log.warn("Could not record {}: {}", ex.javaClass.name, failure.toString())
        } finally {
            recording.remove()
        }
    }

    companion object {
        private const val OWN_CODE = "net.blueshell."
        private const val TYPE_LENGTH = 255
        private const val PLACE_LENGTH = 512

        /**
         * The first frame in the api's own code, or the first frame at all. The line is left out:
         * it moves with every edit above it, and a fault must stay one fault across deploys.
         */
        fun thrownAt(ex: Throwable): String {
            val frame = ex.stackTrace.firstOrNull { it.className.startsWith(OWN_CODE) } ?: ex.stackTrace.firstOrNull()
            return frame?.let { "${it.className}.${it.methodName}" } ?: "unknown"
        }

        fun fingerprint(
            type: String,
            thrownAt: String,
        ): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest("$type@$thrownAt".toByteArray())
                .joinToString("") { "%02x".format(it) }
    }
}
