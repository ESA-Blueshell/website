package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlace
import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlaceRepository
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.shared.tracking.Actor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import tools.jackson.databind.json.JsonMapper

// The places as the repository's SQL keeps them: one row per channel, added once.
private class HeldPlaces : SeenEmojiBackfillPlaceRepository {
    val held = sortedMapOf<String, SeenEmojiBackfillPlace>()

    override fun add(channelId: String): Int {
        val added = held.putIfAbsent(channelId, SeenEmojiBackfillPlace(channelId, null, false)) == null
        return if (added) 1 else 0
    }

    override fun findFirstByDoneFalseOrderByIdAsc(): SeenEmojiBackfillPlace? = held.values.firstOrNull { !it.done }

    override fun move(
        channelId: String,
        before: String?,
        done: Boolean,
    ): Int {
        held[channelId] = SeenEmojiBackfillPlace(channelId, before, done)
        return 1
    }
}

// A server's history: each place's messages oldest first, IDs rising with age as Discord's do.
private class HeldHistory(
    val messages: MutableMap<String, List<Map<String, Any?>>>,
) : DiscordHistory {
    override val selfId = "b1"
    val unreadable = mutableSetOf<String>()

    override fun places(): List<String> = messages.keys.toList()

    override fun page(
        channelId: String,
        before: String?,
    ): List<Map<String, Any?>>? {
        if (channelId in unreadable) return null
        val older = messages.getValue(channelId).filter { before == null || (it["id"] as String).toLong() < before.toLong() }
        return older.reversed().take(PAGE)
    }
}

private fun message(
    id: Long,
    content: String = "",
    author: String = "u1",
    reactions: List<Map<String, Any?>> = emptyList(),
) = mapOf(
    "id" to "$id",
    "timestamp" to "2026-10-24T12:00:00Z",
    "author" to mapOf("id" to author),
    "content" to content,
    "reactions" to reactions,
)

class BackfillSeenEmojiJobTest {
    private val mapper = JsonMapper.builder().build()
    private val places = HeldPlaces()
    private val index = seenEmojiIndex()
    private val queued = mutableListOf<Any>()
    private val jobs =
        object : JobQueue {
            override fun <T : Any> runAsync(
                job: JobDefinition<T>,
                payload: T,
                trigger: JobTrigger,
                actor: Actor?,
            ): QueuedJob? {
                queued += payload
                return null
            }
        }

    private fun job(history: DiscordHistory?): BackfillSeenEmojiJob {
        val provider: ObjectProvider<DiscordHistory> = mock { on { ifAvailable } doReturn history }
        return BackfillSeenEmojiJob(mapper, SeenEmojiBackfill(provider, places, index), jobs)
    }

    private fun BackfillSeenEmojiJob.run(payload: DiscordJobs.BackfillSeenEmojiPayload = DiscordJobs.BackfillSeenEmojiPayload()) =
        handle(mapper.writeValueAsString(payload), 1, forced = false)

    // Runs the job, then every run it queues, as the job queue would.
    private fun BackfillSeenEmojiJob.runToTheEnd() {
        run()
        while (queued.isNotEmpty()) run(queued.removeFirst() as DiscordJobs.BackfillSeenEmojiPayload)
    }

    private fun uses() = index.seen().associate { it.name to it.uses }

    private val kat = "<:spoopykat:111>"
    private val pumpkin = mapOf("emoji" to mapOf("id" to "222", "name" to "pumpkin", "animated" to true), "count" to 3)

    @Test
    fun `reads every place's full history for seen emoji, in text and reactions, a slice per run`() {
        val long = (1L..2500L).map { if (it % 1000 == 0L) message(it, "carve $kat") else message(it) }
        val history =
            HeldHistory(
                mutableMapOf(
                    "c1" to long,
                    "t1" to listOf(message(1, reactions = listOf(pumpkin)), message(2, "🎃 <:shellywave:42>", author = "b1")),
                ),
            )

        job(history).runToTheEnd()

        assertThat(uses()).isEqualTo(mapOf("spoopykat" to 2L, "pumpkin" to 1L))
        assertThat(places.held.values.map { it.done }).containsOnly(true)
    }

    @Test
    fun `resumes where a stopped run was, and a rerun counts nothing twice`() {
        val history = HeldHistory(mutableMapOf("c1" to (1L..4000L).map { message(it, "carve $kat") }))
        job(history).run()
        queued.clear()

        job(history).run(DiscordJobs.BackfillSeenEmojiPayload(discover = false))
        queued.clear()
        assertThat(uses()).isEqualTo(mapOf("spoopykat" to 4000L))

        history.messages["c2"] = listOf(message(9001, kat))
        job(history).runToTheEnd()
        assertThat(uses()).isEqualTo(mapOf("spoopykat" to 4001L))
    }

    @Test
    fun `skips a place the bot may no longer read`() {
        val history = HeldHistory(mutableMapOf("c1" to listOf(message(1, kat)), "c2" to listOf(message(2, kat))))
        history.unreadable += "c1"

        job(history).runToTheEnd()

        assertThat(uses()).isEqualTo(mapOf("spoopykat" to 1L))
        assertThat(places.held.getValue("c1").done).isTrue()
    }

    @Test
    fun `skips without a bot`() {
        assertThat(job(null).run()).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat(job(null).run(DiscordJobs.BackfillSeenEmojiPayload(discover = false))).isInstanceOf(JobOutcome.Done::class.java)
        assertThat(queued).isEmpty()
    }

    @Test
    fun `reads no emoji from a message missing what it needs, or from a reaction only the bot made`() {
        val mine = mapOf("emoji" to mapOf("id" to "222", "name" to "pumpkin"), "count" to 1, "me" to true)
        val standard = mapOf("emoji" to mapOf("id" to null, "name" to "🎃"), "count" to 2)
        val nameless = mapOf("emoji" to mapOf("id" to "333"), "count" to 2)

        assertThat(sightingsIn(mapOf("timestamp" to "2026-10-24T12:00:00Z", "content" to kat), "b1")).isEmpty()
        assertThat(sightingsIn(mapOf("id" to "1", "content" to kat), "b1")).isEmpty()
        assertThat(sightingsIn(message(1, reactions = listOf(mine, standard, nameless, mapOf("count" to 2))), "b1")).isEmpty()
    }
}
