package net.blueshell.api.exceptions.api

import jakarta.annotation.PreDestroy
import net.blueshell.api.exceptions.persistence.RecordedExceptionRepository
import net.blueshell.api.shared.util.PersonalDetails
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.TransactionTemplate
import java.security.MessageDigest
import java.time.Clock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * Records one occurrence of an exception against its fault, creating the fault the first time
 * and reopening it if it was resolved.
 *
 * Recording never throws and never recurses: it runs in its own transaction so a rolled-back
 * caller cannot take it along, a failure while recording is logged and dropped, and an exception
 * raised while this thread is already recording is not recorded again. The message and the trace
 * are scrubbed of personal details first, as a job's recorded failure is.
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

    // One thread and a short queue: a burst of faults neither holds the threads that met them nor piles up.
    private val aside =
        ThreadPoolExecutor(
            1,
            1,
            0,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(WAITING),
            { work -> Thread(work, "exception-recorder").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardPolicy(),
        )

    /**
     * Records off the caller's thread, for a request: with every connection taken, recording in
     * line would hold each failing request for as long as the pool makes it wait. Dropped when
     * [WAITING] are already waiting.
     */
    fun recordAside(
        ex: Throwable,
        concern: ExceptionConcern,
    ) = aside.execute { record(ex, concern) }

    @PreDestroy
    fun stop() = aside.shutdown()

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
                    message = ex.message?.let(PersonalDetails::scrub),
                    stackTrace = PersonalDetails.scrub(ex.stackTraceToString()),
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
        private const val WAITING = 100

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
