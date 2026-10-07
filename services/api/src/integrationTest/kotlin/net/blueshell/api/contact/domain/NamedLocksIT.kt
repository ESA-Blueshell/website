package net.blueshell.api.contact.domain

import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@SpringBootTest
class NamedLocksIT : ServiceTestSupport() {
    @Autowired
    private lateinit var locks: NamedLocks

    @Test
    fun `a second holder of one name waits until the first lets it go`() {
        val taken = CountDownLatch(1)
        val release = CountDownLatch(1)
        val order = mutableListOf<String>()
        val first =
            thread {
                locks.holding("it-lock") {
                    synchronized(order) { order += "first in" }
                    taken.countDown()
                    release.await(5, TimeUnit.SECONDS)
                    synchronized(order) { order += "first out" }
                }
            }
        taken.await(5, TimeUnit.SECONDS)
        val second = thread { locks.holding("it-lock") { synchronized(order) { order += "second in" } } }
        Thread.sleep(300)
        release.countDown()
        first.join(5000)
        second.join(5000)

        assertThat(order).containsExactly("first in", "first out", "second in")
    }
}
