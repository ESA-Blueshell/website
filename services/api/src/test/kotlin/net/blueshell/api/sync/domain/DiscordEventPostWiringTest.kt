package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventChange
import net.blueshell.api.event.api.EventChanged
import net.blueshell.api.event.api.EventPostData
import net.blueshell.api.event.api.EventPosts
import net.blueshell.api.event.api.EventSignUpsChanged
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.sync.api.ExternalIdMappingService
import net.blueshell.api.sync.persistence.ExternalIdMapping
import net.blueshell.api.sync.persistence.ExternalIdMappingRepository
import net.blueshell.api.sync.web.DiscordPostsDevController
import net.blueshell.api.testsupport.runJob
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
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
    fun `runs each job on the event it names`() {
        val posts: DiscordEventPosts =
            mock {
                on { keepAnnouncement(42, false) } doReturn Kept(JobEffect.MADE)
                on { keepAnnouncement(7, false) } doReturn Kept()
                on { keepCalendarPost(42, false) } doReturn Kept()
                on { keepDiscordEvent(42) } doReturn Kept()
            }
        val repository: ExternalIdMappingRepository = mock { on { acquireNamedLock(any(), any()) } doReturn 1 }
        val lock = DiscordEventLock(repository)
        val announcement = DiscordAnnouncementJob(mapper, posts, lock)
        val calendar = DiscordCalendarPostJob(mapper, posts, lock)
        val listing = DiscordEventJob(mapper, posts, lock)

        announcement.runJob("""{"eventId": 42}""", null)
        announcement.runJob("""{"eventId": 7}""", null)
        calendar.runJob("""{"eventId": 42}""", null)
        listing.runJob("""{"eventId": 42}""", null)

        verify(posts).keepCalendarPost(42, false)
        verify(posts).keepDiscordEvent(42)
        verify(repository, times(4)).releaseNamedLock(any())
        assertThat(listOf(announcement.jobType, calendar.jobType, listing.jobType))
            .containsExactly("discord.announcement", "discord.post", "discord.event")
        assertThat(listOf(announcement, calendar, listing).map { it.retrySchedule.maxRetries }).containsOnly(10)
    }

    @Test
    fun `hands a forced run on, and ends a run that did nothing as skipped with its reason`() {
        val posts: DiscordEventPosts =
            mock {
                on { keepAnnouncement(42, true) } doReturn Kept(skipped = "The event is over.")
                on { keepCalendarPost(42, true) } doReturn Kept(skipped = "The event's day is over.")
                on { keepDiscordEvent(42) } doReturn Kept(JobEffect.MADE, "https://discord.test/events/e1")
            }
        val repository: ExternalIdMappingRepository = mock { on { acquireNamedLock(any(), any()) } doReturn 1 }
        val lock = DiscordEventLock(repository)

        assertThat(DiscordAnnouncementJob(mapper, posts, lock).runJob("""{"eventId": 42}""", forced = true))
            .isEqualTo(JobOutcome.Skipped("The event is over."))
        assertThat(DiscordCalendarPostJob(mapper, posts, lock).runJob("""{"eventId": 42}""", forced = true))
            .isEqualTo(JobOutcome.Skipped("The event's day is over."))
        assertThat(DiscordEventJob(mapper, posts, lock).runJob("""{"eventId": 42}""", forced = true))
            .isEqualTo(JobOutcome.Done(JobEffect.MADE, "https://discord.test/events/e1"))
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
            // The board chose the morning of Saturday 26 September when it approved the event.
            announceAt = at("2026-09-26T08:00"),
        )

    private class Queued : JobQueue {
        val types = mutableListOf<String>()
        val triggers = mutableListOf<Pair<JobTrigger, Actor?>>()

        override fun <T : Any> runAsync(
            job: JobDefinition<T>,
            payload: T,
            trigger: JobTrigger,
            actor: Actor?,
        ): QueuedJob? {
            types += "${job.type} $payload"
            triggers += trigger to actor
            return null
        }

        override fun runAgain(
            executionId: Long,
            trigger: JobTrigger,
        ): QueuedJob? = null
    }

    private fun triggers(
        now: String,
        found: EventPostData? = lan,
        out: Set<DiscordArtefact> = emptySet(),
        jobs: JobQueue = Queued(),
    ): DiscordEventPostTriggers {
        val events: EventPosts =
            mock {
                on { of(42) } doReturn found
                on { keptEndingFrom(any()) } doReturn listOf(42L)
            }
        val ledger: PostLedger = mock()
        out.forEach { whenever(ledger.find(42, it)).thenReturn(RecordedArtefact("m", 1)) }
        return DiscordEventPostTriggers(jobs, events, ledger).apply { clock = Clock.fixed(at(now), ZoneOffset.UTC) }
    }

    private fun queuedBy(
        now: String,
        found: EventPostData? = lan,
        out: Set<DiscordArtefact> = emptySet(),
        run: DiscordEventPostTriggers.() -> Unit,
    ): List<String> {
        val jobs = Queued()
        triggers(now, found, out, jobs).run()
        return jobs.types.map { it.substringBefore(' ') }
    }

    private val all = listOf("discord.announcement", "discord.post", "discord.event")

    @Test
    fun `queues all three for every change, whatever is due or out, and both posts for a change in sign-ups`() {
        val changed: DiscordEventPostTriggers.() -> Unit = { on(EventChanged(42, EventChange.UPDATED)) }

        assertThat(queuedBy("2026-08-01T10:00", run = changed)).isEqualTo(all)
        assertThat(queuedBy("2026-10-10T10:00", found = null, run = changed)).isEqualTo(all)
        assertThat(queuedBy("2026-10-10T10:00") { on(EventSignUpsChanged(42)) }).containsExactly("discord.announcement", "discord.post")
    }

    @Test
    fun `queues each morning the Discord event until the event is over, and the posts when they are due`() {
        val morning: DiscordEventPostTriggers.() -> Unit = { runMorning() }

        assertThat(queuedBy("2026-06-01T08:00", run = morning)).containsExactly("discord.event")
        assertThat(queuedBy("2026-09-26T07:59", run = morning)).containsExactly("discord.event")
        assertThat(queuedBy("2026-09-26T08:00", run = morning)).containsExactly("discord.announcement", "discord.event")
        assertThat(queuedBy("2026-10-10T10:00", run = morning)).isEqualTo(all)
        assertThat(queuedBy("2026-10-11T01:00", found = lan.copy(endTime = at("2026-10-11T03:00")), run = morning)).isEqualTo(all)
        assertThat(queuedBy("2026-10-11T00:00", out = DiscordArtefact.entries.toSet(), run = morning))
            .containsExactly("discord.announcement", "discord.post")
    }

    @Test
    fun `queues each morning what is out, to edit or remove it`() {
        val morning: DiscordEventPostTriggers.() -> Unit = { runMorning() }

        assertThat(queuedBy("2026-09-20T08:00", found = lan.copy(live = false), out = setOf(DiscordArtefact.INFO_POST), run = morning))
            .containsExactly("discord.announcement")
        assertThat(queuedBy("2026-10-10T10:00", found = lan.copy(live = false), out = DiscordArtefact.entries.toSet(), run = morning))
            .isEqualTo(all)
        assertThat(queuedBy("2026-10-10T10:00", found = null, run = morning)).isEmpty()
    }

    @Test
    fun `looks each morning at every kept event that ended at most two days ago or ends later`() {
        val jobs = Queued()
        val events: EventPosts =
            mock {
                on { keptEndingFrom(at("2026-09-24T08:00")) } doReturn listOf(42L)
                on { of(42) } doReturn lan
            }
        val triggers = DiscordEventPostTriggers(jobs, events, mock()).apply { clock = Clock.fixed(at("2026-09-26T08:00"), ZoneOffset.UTC) }

        triggers.morning()

        assertThat(jobs.types).containsExactly(
            "discord.announcement EventPostPayload(eventId=42)",
            "discord.event EventPostPayload(eventId=42)",
        )
        assertThat(jobs.triggers.map { it.first }).containsOnly(JobTrigger.MORNING_RUN)
    }

    @Test
    fun `says what queued each job and who made the change`() {
        val board = Actor.user(5, Role.BOARD)
        val jobs = Queued()
        triggers("2026-10-10T10:00", jobs = jobs).on(EventChanged(42, EventChange.APPROVED, board))
        triggers("2026-10-10T10:00", out = setOf(DiscordArtefact.INFO_POST), jobs = jobs).on(EventSignUpsChanged(42, board))
        triggers("2026-10-10T10:00", jobs = jobs).on(EventChanged(42, EventChange.CREATED, board))
        triggers("2026-10-10T10:00", jobs = jobs).on(EventChanged(42, EventChange.UPDATED, board))
        triggers("2026-10-10T10:00", found = lan.copy(live = false), out = setOf(DiscordArtefact.INFO_POST), jobs = jobs)
            .on(EventChanged(42, EventChange.UNAPPROVED, board))
        triggers("2026-10-10T10:00", found = null, out = setOf(DiscordArtefact.INFO_POST), jobs = jobs)
            .on(EventChanged(42, EventChange.DELETED, board))
        triggers("2026-10-10T10:00", jobs = jobs).on(EventChanged(42, EventChange.SENT_BACK, board))

        assertThat(jobs.triggers.distinct()).containsExactly(
            JobTrigger.EVENT_APPROVED to board,
            JobTrigger.SIGN_UPS_CHANGED to board,
            JobTrigger.EVENT_CREATED to board,
            JobTrigger.EVENT_UPDATED to board,
            JobTrigger.EVENT_UNAPPROVED to board,
            JobTrigger.EVENT_DELETED to board,
            JobTrigger.EVENT_SENT_BACK to board,
        )
    }

    @Test
    fun `holds one event's lock around its work, and gives up where another run keeps it`() {
        val repository: ExternalIdMappingRepository = mock()
        whenever(repository.acquireNamedLock("discord-event-42", 60)).thenReturn(1, 0)
        val lock = DiscordEventLock(repository)

        assertThat(lock.holding(42) { "done" }).isEqualTo("done")
        assertThatThrownBy { lock.holding(42) { error("never runs") } }.hasMessageContaining("still running")

        verify(repository, times(1)).releaseNamedLock("discord-event-42")
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
