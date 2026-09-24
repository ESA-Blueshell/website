package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.event.domain.EventChange
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.DiscordPostTrigger
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.web.DiscordPostsDevController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class DiscordEventPostWiringTest {
    private val eight = Instant.parse("2026-09-26T06:00:00Z")

    @Test
    fun `runs a queued reconcile as the payload says, judging now where it names no moment`() {
        val posts: DiscordEventPosts = mock()
        val job = DiscordEventPostJob(JsonMapper.builder().build(), posts)
        job.clock = Clock.fixed(eight, ZoneOffset.UTC)

        job.handle("""{"eventId": 42, "trigger": "MORNING", "at": "1970-01-01T00:00:00Z"}""", null)
        job.handle("""{"eventId": 42, "trigger": "CHANGE"}""", null)

        verify(posts).reconcile(42, DiscordPostTrigger.MORNING, Instant.EPOCH)
        verify(posts).reconcile(42, DiscordPostTrigger.CHANGE, eight)
        assertThat(job.jobType).isEqualTo("discord.reconcile-event-posts")
        assertThat(job.retrySchedule.maxRetries).isEqualTo(10)
    }

    @Test
    fun `queues a reconcile on every change, and one per event near its time each morning`() {
        val jobs: JobQueue = mock()
        val events: EventPosts =
            mock { on { approvedOverlapping(eight.minusSeconds(2 * 86_400), eight.plusSeconds(15 * 86_400)) } doReturn listOf(7L, 8L) }
        val triggers = DiscordEventPostTriggers(jobs, events)
        triggers.clock = Clock.fixed(eight, ZoneOffset.UTC)

        whenever(events.approvedOverlapping(eight.minusSeconds(2 * 3_600), eight)).thenReturn(listOf(9L))
        triggers.on(EventChanged(42, EventChange.UPDATED))
        triggers.morning()
        triggers.hourly()

        verify(jobs).runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(42, DiscordPostTrigger.CHANGE))
        verify(jobs).runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(7, DiscordPostTrigger.MORNING, eight))
        verify(jobs).runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(8, DiscordPostTrigger.MORNING, eight))
        verify(jobs).runAsync(DiscordPostJobs.Reconcile, DiscordPostJobs.ReconcilePayload(9, DiscordPostTrigger.CHANGE))
    }

    @Test
    fun `runs the morning run as if it were a given moment in Amsterdam, on the dev profile`() {
        val triggers: DiscordEventPostTriggers = mock { on { runMorning(eight) } doReturn 3 }

        assertThat(DiscordPostsDevController(triggers).run(LocalDateTime.parse("2026-09-26T08:00"))).isEqualTo(mapOf("events" to 3))
    }

    @Test
    fun `keeps the ledger in the external ID mappings, a claim in progress reading as nothing out there`() {
        val mappings: ExternalIdMappingService = mock()
        val ledger = MappingPostLedger(mappings)
        val info = ExternalIdMapping("EVENT", 42, "DISCORD_EVENTS_INFO", "m1", 7)
        whenever(mappings.find("EVENT", 42, "DISCORD_EVENTS_INFO")).thenReturn(info)
        whenever(mappings.find("EVENT", 42, "DISCORD_EVENT")).thenReturn(ExternalIdMapping("EVENT", 42, "DISCORD_EVENT"))
        whenever(mappings.claim(any(), any(), any(), any())).thenReturn(true)

        assertThat(ledger.find(42, DiscordArtefact.INFO_POST)).isEqualTo(RecordedArtefact("m1", 7))
        assertThat(ledger.find(42, DiscordArtefact.DISCORD_EVENT)).isNull()
        assertThat(ledger.find(42, DiscordArtefact.CALENDAR_POST)).isNull()
        assertThat(ledger.claim(42, DiscordArtefact.CALENDAR_POST, eight)).isTrue()
        ledger.record(42, DiscordArtefact.CALENDAR_POST, "m3", 9)
        ledger.release(42, DiscordArtefact.CALENDAR_POST)

        verify(mappings).claim("EVENT", 42, "DISCORD_EVENTS_CALENDAR", eight.minusSeconds(15 * 60))
        verify(mappings).record("EVENT", 42, "DISCORD_EVENTS_CALENDAR", "m3", 9)
        verify(mappings).release("EVENT", 42, "DISCORD_EVENTS_CALENDAR")
    }

    @Test
    fun `reads a fingerprint that was never written as none`() {
        val mappings: ExternalIdMappingService = mock()
        whenever(mappings.find("EVENT", 42, "DISCORD_EVENTS_INFO")).thenReturn(ExternalIdMapping("EVENT", 42, "DISCORD_EVENTS_INFO", "m1"))

        assertThat(MappingPostLedger(mappings).find(42, DiscordArtefact.INFO_POST)).isEqualTo(RecordedArtefact("m1", 0))
    }
}
