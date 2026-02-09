package net.blueshell.api.shared.event

import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class AfterCommitEventPublisherIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var publisher: AfterCommitEventPublisher

    @Test
    fun `publishes after commit`() {
        transactionTemplate.execute {
            publisher.publish(TestEvent("payload"))
            assertEquals(0, applicationEvents.stream(TestEvent::class.java).count())
        }

        assertEquals(1, applicationEvents.stream(TestEvent::class.java).count())
    }

    data class TestEvent(val value: String)
}
