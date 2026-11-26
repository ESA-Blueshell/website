package net.blueshell.api.sync.domain

import net.blueshell.api.event.api.EventService
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.platform.config.JacksonConfig
import net.blueshell.api.shared.job.JobCount
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobEffect
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTally
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.sync.api.CustomEmoji
import net.blueshell.api.sync.api.DiscordPublisher
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.time.Instant

class EventShortcodesTest {
    private val mapper = JacksonConfig().jsonMapper(null)
    private val events: EventService = mock()
    private val custom =
        listOf(
            CustomEmoji("datboythinK", "<:datboythinK:1>"),
            CustomEmoji("fire", "<a:fire:2>"),
            CustomEmoji("FIRE", "<:FIRE:3>"),
            CustomEmoji("fire", "<:fire:4>"),
        )
    private val bot: DiscordPublisher = mock { on { customEmoji() } doReturn custom }

    private fun shortcodes(publisher: DiscordPublisher? = bot): EventShortcodes {
        val provider: ObjectProvider<DiscordPublisher> = mock { on { ifAvailable } doReturn publisher }
        return EventShortcodes(events, provider)
    }

    private fun job(publisher: DiscordPublisher? = bot) = ResolveEmojiShortcodesJob(mapper, shortcodes(publisher))

    private fun ResolveEmojiShortcodesJob.run(eventId: Long = 7) = handle("""{"eventId":$eventId}""", 1, forced = false)

    private fun event(description: String?) = Entities.event(id = 7).apply { this.description = description }

    private fun holding(description: String?) =
        event(description).also { whenever(events.findByIdIncludingDeletedOrNull(7)).thenReturn(it) }

    @Test
    fun `names the server's own first, an exact case before any case, then the standard set`() {
        val emojiOf = shortcodes().lookup()!!

        assertThat(emojiOf("datboythinK")).isEqualTo("<:datboythinK:1>")
        assertThat(emojiOf("DATBOYTHINK")).isEqualTo("<:datboythinK:1>")
        assertThat(emojiOf("fire")).isEqualTo("<a:fire:2>")
        assertThat(emojiOf("FIRE")).isEqualTo("<:FIRE:3>")
        assertThat(emojiOf("Fire")).isEqualTo("<a:fire:2>")
        assertThat(emojiOf("Smile")).isEqualTo("😄")
        assertThat(emojiOf("nope")).isNull()
    }

    @Test
    fun `writes the emoji into the description, saving it as an edit, and reports what it did and left`() {
        val held = holding("Think :datboythinK: :smile: at 12:30:45, :nope: `:fire:`")

        val outcome = job().run() as JobOutcome.Done

        assertThat(held.description).isEqualTo("Think <:datboythinK:1> 😄 at 12:30:45, :nope: `:fire:`")
        verify(events).update(held)
        assertThat(outcome.effect).isEqualTo(JobEffect.EDITED)
        assertThat(outcome.counts).containsExactly(
            JobCount(JobTally.SHORTCODES_RESOLVED, 2, names = listOf("datboythinK", "smile")),
            JobCount(JobTally.SHORTCODES_LEFT, 1, names = listOf("nope")),
        )
    }

    @Test
    fun `leaves a description with nothing to write alone`() {
        holding(null)

        val outcome = job().run() as JobOutcome.Done

        verify(events, never()).update(any())
        assertThat(outcome.effect).isEqualTo(JobEffect.UNCHANGED)
    }

    @Test
    fun `skips an event that is gone, and without a bot`() {
        whenever(events.findByIdIncludingDeletedOrNull(7)).thenReturn(event("x").apply { deletedAt = Instant.EPOCH })

        assertThat((job().run() as JobOutcome.Skipped).reason).isEqualTo("The event is gone")
        assertThat((job().run(8) as JobOutcome.Skipped).reason).isEqualTo("The event is gone")
        assertThat((job(null).run() as JobOutcome.Skipped).reason).isEqualTo("The Discord bot is not set up")
    }

    @Test
    fun `queues a resolve for every event with a shortcode to write, past and upcoming alike, and none for the rest`() {
        whenever(events.descriptionsWithColons()).thenReturn(
            mapOf(9L to "Doors at 12:30:45, :nope:", 4L to "Carve :fire:", 2L to "`:fire:` and :smile:"),
        )
        val queued = mutableListOf<Long>()
        val jobs =
            object : JobQueue {
                override fun <T : Any> runAsync(
                    job: JobDefinition<T>,
                    payload: T,
                    trigger: JobTrigger,
                    actor: Actor?,
                ): QueuedJob? {
                    assertThat(job).isEqualTo(EventTextJobs.ResolveEmojiShortcodes)
                    queued += (payload as EventTextJobs.EventShortcodesPayload).eventId
                    return null
                }
            }
        val all = ResolveAllEmojiShortcodesJob(mapper, shortcodes(), jobs)

        val outcome = all.handle("{}", 1, forced = false) as JobOutcome.Done

        assertThat(queued).containsExactly(2L, 4L)
        assertThat(outcome.counts).containsExactly(JobCount(JobTally.EVENTS_QUEUED, 2))
        assertThat((ResolveAllEmojiShortcodesJob(mapper, shortcodes(null), jobs).handle("{}", 1, false) as JobOutcome.Skipped).reason)
            .isEqualTo("The Discord bot is not set up")
    }
}
