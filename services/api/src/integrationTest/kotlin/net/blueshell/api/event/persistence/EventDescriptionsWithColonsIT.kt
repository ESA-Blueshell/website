package net.blueshell.api.event.persistence

import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest
class EventDescriptionsWithColonsIT : UserTestSupport() {
    @Autowired
    private lateinit var events: EventRepository

    private fun event(description: String?): Event {
        val start = Instant.now().minus(30, ChronoUnit.DAYS)
        return persist(
            Event(
                committee = createCommitteeFixture(),
                title = "Carving",
                description = description,
                startTime = start,
                endTime = start.plus(3, ChronoUnit.HOURS),
            ),
        )
    }

    @Test
    fun `reads the description of each live event with two colons, past ones included, and nothing more`() {
        val named = event("Carve :pumpkin: pumpkins")
        val timed = event("Doors at 12:30:45")
        val oneColon = event("Note: bring a knife")
        val none = event(null)
        val gone = event("Carve :pumpkin:").also { gone -> transactionTemplate.execute { events.deleteById(gone.id!!) } }

        val read = events.findDescriptionsWithColons().associate { it.id to it.description }

        assertThat(read).containsEntry(named.id, "Carve :pumpkin: pumpkins").containsEntry(timed.id, "Doors at 12:30:45")
        assertThat(read).doesNotContainKeys(oneColon.id, none.id, gone.id)
    }
}
