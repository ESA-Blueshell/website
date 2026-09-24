package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.event.api.EventSignUpsChanged
import net.blueshell.api.event.domain.EventChange
import net.blueshell.api.shared.job.DiscordPostJobs
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.web.DiscordPostsDevController
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

class DiscordEventPostWiringTest {
    private val eight = Instant.parse("2026-09-26T06:00:00Z")

    private fun at(local: String): Instant = LocalDateTime.parse(local).atZone(DiscordPostSchedule.ZONE).toInstant()

    private val mapper = JsonMapper.builder().build()

    @Test
    fun `runs each job on the event it names, queueing the Discord event once the events-info post is up`() {
        val posts: DiscordEventPosts = mock { on { keepAnnouncement(42) } doReturn true }
        val jobs: JobQueue = mock()
        val announcement = DiscordAnnouncementJob(mapper, posts, jobs)
        val calendar = DiscordCalendarPostJob(mapper, posts)
        val listing = DiscordEventJob(mapper, posts)

        announcement.handle("""{"eventId": 42}""", null)
        announcement.handle("""{"eventId": 7}""", null)
        calendar.handle("""{"eventId": 42}""", null)
        listing.handle("""{"eventId": 42}""", null)

        verify(posts).keepCalendarPost(42)
        verify(posts).keepDiscordEvent(42)
        verify(jobs).runAsync(DiscordPostJobs.DiscordEvent, DiscordPostJobs.EventPostPayload(42))
        verify(jobs, never()).runAsync(DiscordPostJobs.DiscordEvent, DiscordPostJobs.EventPostPayload(7))
        assertThat(listOf(announcement.jobType, calendar.jobType, listing.jobType))
            .containsExactly("discord.announcement", "discord.post", "discord.event")
        assertThat(listOf(announcement, calendar, listing).map { it.retrySchedule?.maxRetries }).containsOnly(10)
    }

    private val lan =
        EventPostData(
            id = 42,
            live = true,
            title = "LAN party",
            description = null,
            location = null,
            startTime = at("2026-10-10T20:00"),
            endTime = at("2026-10-10T23:00"),
            memberPrice = null,
            publicPrice = null,
            membersOnly = false,
            signUp = false,
            signUpCount = 0,
            signUpLimit = null,
            signUpDeadline = null,
            pingedRoleIds = emptyList(),
            bannerPath = null,
        )

    private class Queued : JobQueue {
        val types = mutableListOf<String>()

        override fun <T : Any> runAsync(
            job: JobDefinition<T>,
            payload: T,
            actor: Actor?,
        ): QueuedJob? {
            types += "${job.type} $payload"
            return null
        }
    }

    private fun triggers(
        now: String,
        found: EventPostData? = lan,
        out: Set<DiscordArtefact> = emptySet(),
        jobs: JobQueue = Queued(),
    ): DiscordEventPostTriggers {
        val events: EventPosts = mock { on { of(42) } doReturn found }
        val ledger: PostLedger = mock()
        out.forEach { whenever(ledger.find(42, it)).thenReturn(RecordedArtefact("m", 1)) }
        return DiscordEventPostTriggers(jobs, events, ledger).apply { clock = Clock.fixed(at(now), ZoneOffset.UTC) }
    }

    private fun changed(
        now: String,
        found: EventPostData? = lan,
        out: Set<DiscordArtefact> = emptySet(),
    ): List<String> {
        val jobs = Queued()
        triggers(now, found, out, jobs).on(EventChanged(42, EventChange.UPDATED))
        return jobs.types.map { it.substringBefore(' ') }
    }

    @Test
    fun `leaves a late events-info post for the next morning run, unless the event's day has come`() {
        assertThat(changed("2026-10-05T10:00")).isEmpty()
        assertThat(changed("2026-10-10T10:00")).containsExactly("discord.announcement", "discord.post")
    }

    @Test
    fun `queues a change for what is out, to edit or remove it`() {
        val everything = DiscordArtefact.entries.toSet()

        assertThat(changed("2026-10-05T10:00", out = setOf(DiscordArtefact.INFO_POST)))
            .containsExactly("discord.announcement", "discord.event")
        assertThat(changed("2026-10-10T10:00", found = lan.copy(live = false), out = everything))
            .containsExactly("discord.announcement", "discord.post", "discord.event")
        assertThat(changed("2026-10-10T23:30", found = null, out = setOf(DiscordArtefact.INFO_POST)))
            .containsExactly("discord.announcement")
        assertThat(changed("2026-10-10T10:00", found = null)).isEmpty()
    }

    @Test
    fun `queues each morning what is due for every event near its time, and hourly the Discord events of events ending`() {
        val jobs = Queued()
        val events: EventPosts =
            mock {
                on { approvedOverlapping(at("2026-09-24T08:00"), at("2026-10-11T08:00")) } doReturn listOf(42L)
                on { approvedOverlapping(at("2026-09-26T06:00"), at("2026-09-26T08:00")) } doReturn listOf(42L, 43L)
                on { of(42) } doReturn lan
            }
        val ledger: PostLedger = mock { on { find(42, DiscordArtefact.DISCORD_EVENT) } doReturn RecordedArtefact("e1", 1) }
        val triggers = DiscordEventPostTriggers(jobs, events, ledger).apply { clock = Clock.fixed(at("2026-09-26T08:00"), ZoneOffset.UTC) }

        triggers.morning()
        triggers.hourly()

        assertThat(jobs.types).containsExactly(
            "discord.announcement EventPostPayload(eventId=42)",
            "discord.event EventPostPayload(eventId=42)",
            "discord.event EventPostPayload(eventId=42)",
        )
    }

    @Test
    fun `queues an edit of the posts already out when the sign-up count moves`() {
        val jobs = Queued()
        triggers("2026-10-10T10:00", out = setOf(DiscordArtefact.INFO_POST, DiscordArtefact.CALENDAR_POST), jobs = jobs)
            .on(EventSignUpsChanged(42))
        triggers("2026-10-10T10:00", jobs = jobs).on(EventSignUpsChanged(42))

        assertThat(jobs.types.map { it.substringBefore(' ') }).containsExactly("discord.announcement", "discord.post")
    }

    @Test
    fun `runs the morning run now, on the dev profile`() {
        val triggers: DiscordEventPostTriggers = mock { on { runMorning() } doReturn 3 }

        assertThat(DiscordPostsDevController(triggers).run()).isEqualTo(mapOf("events" to 3))
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
