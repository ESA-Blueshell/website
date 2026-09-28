package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.event.persistence.Event
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@SpringBootTest
class DiscordEventPostTriggersIT : UserTestSupport() {
    @Autowired private lateinit var events: EventService

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var tx: TransactionTemplate

    private fun jobsFor(eventId: Long): List<String?> =
        jdbc.queryForList(
            "SELECT job_type FROM job_executions WHERE job_type LIKE 'discord.%' AND payload LIKE ? ORDER BY job_type",
            String::class.java,
            "%\"eventId\":$eventId}%",
        )

    private fun anEvent(): Event =
        events.create(
            Event(
                committee = createCommitteeFixture(),
                title = "LAN party",
                startTime = Instant.now().plusSeconds(86_400 * 40L),
                endTime = Instant.now().plusSeconds(86_400 * 40L + 7200),
                approved = true,
            ),
        )

    @Test
    fun `a change commits with its three Discord jobs, and one rolled back leaves none`() {
        val kept = anEvent()
        var rolledBack: Long? = null
        tx.executeWithoutResult { status ->
            rolledBack = anEvent().id
            status.setRollbackOnly()
        }

        assertThat(jobsFor(kept.id!!)).containsExactly("discord.announcement", "discord.event", "discord.post")
        assertThat(jobsFor(rolledBack!!)).isEmpty()
    }
}
