package net.blueshell.api.testsupport

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicLong

/**
 * The statements [block] issues on the calling thread.
 *
 * Hibernate's statistics count the whole session factory, so the job retry sweep — every 100ms
 * under the test profile — lands in whichever measurement it overlaps and a fetch test reads a
 * statement that is not its own. Counting the SQL log instead, and only what this thread logs,
 * leaves a count that only the work being measured moves.
 */
fun countStatements(block: () -> Unit): Long {
    val sql = LoggerFactory.getLogger("org.hibernate.SQL") as Logger
    val counter = OwnThreadCounter(Thread.currentThread())
    val level = sql.level
    val additive = sql.isAdditive

    counter.start()
    sql.addAppender(counter)
    // The statements are counted here, not printed over every other test's output.
    sql.isAdditive = false
    sql.level = Level.DEBUG
    try {
        block()
    } finally {
        sql.level = level
        sql.isAdditive = additive
        sql.detachAppender(counter)
        counter.stop()
    }

    return counter.count.get()
}

/**
 * A logging event resolves its thread name lazily, on whichever thread first asks, so the
 * thread is read here — where the event is still on the one that logged it.
 */
private class OwnThreadCounter(private val thread: Thread) : AppenderBase<ILoggingEvent>() {
    val count = AtomicLong()

    override fun append(eventObject: ILoggingEvent) {
        if (Thread.currentThread() === thread) {
            count.incrementAndGet()
        }
    }
}
