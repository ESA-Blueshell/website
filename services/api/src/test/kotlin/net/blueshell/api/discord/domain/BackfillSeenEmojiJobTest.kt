package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlace
import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlaceRepository
import net.blueshell.api.discord.persistence.SeenEmojiRepository
import net.blueshell.api.jobs.api.JobOutcome
import net.blueshell.api.shared.job.JobDefinition
import net.blueshell.api.shared.job.JobQueue
import net.blueshell.api.shared.job.JobTrigger
import net.blueshell.api.shared.job.QueuedJob
import net.blueshell.api.shared.tracking.Actor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionStatus
import tools.jackson.databind.json.JsonMapper

// The places as SeenEmojiBackfillPlaceRepository's SQL keeps them; SeenEmojiBackfillIT holds the SQL to the same.
private class HeldPlaces : SeenEmojiBackfillPlaceRepository {
    val held = sortedMapOf<String, SeenEmojiBackfillPlace>()

    override fun restart(channelId: String) {
        held[channelId] = SeenEmojiBackfillPlace(channelId, null, false)
    }

    override fun findFirstByDoneFalseOrderByIdAsc(): SeenEmojiBackfillPlace? = held.values.firstOrNull { !it.done }

    override fun markRead(
        channelId: String,
        before: String?,
        done: Boolean,
    ) {
        held[channelId] = SeenEmojiBackfillPlace(channelId, before, done)
    }
}

// A server's history: each place's messages oldest first, with IDs rising as Discord's do.
internal class HeldHistory(
    val messages: MutableMap<String, List<Map<String, Any?>>>,
) : DiscordHistory {
    val unreadable = mutableSetOf<String>()
    var readsText = true

    override fun readsText() = readsText

    override fun places(): List<String> = messages.keys.toList()

    override fun page(
        channelId: String,
        before: String?,
    ): HistoryPage? {
        if (channelId in unreadable) return null
        val older = messages.getValue(channelId).filter { before == null || (it["id"] as String).toLong() < before.toLong() }
        val page = older.reversed().take(100)
        return HistoryPage(page.flatMap { sightingsIn(it, "b1") }, page.lastOrNull()?.get("id") as String? ?: before, page.size < 100)
    }
}

internal fun message(
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
    private val transactions: PlatformTransactionManager = mock { on { getTransaction(anyOrNull()) } doReturn mock<TransactionStatus>() }
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
        return BackfillSeenEmojiJob(mapper, SeenEmojiBackfill(provider, places, index, transactions), jobs)
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

        job(history).run(DiscordJobs.BackfillSeenEmojiPayload(restart = false))
        queued.clear()
        assertThat(uses()).isEqualTo(mapOf("spoopykat" to 4000L))

        history.messages["c2"] = listOf(message(9001, kat))
        job(history).runToTheEnd()
        assertThat(uses()).isEqualTo(mapOf("spoopykat" to 4001L))
        assertThat(places.held.values.map { it.done }).containsOnly(true)
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
    fun `queues the next slice behind the running one, which the queue would otherwise fold it into`() {
        assertThat(DiscordJobs.BackfillSeenEmoji.queuesBehindRunning).isTrue()
    }

    @Test
    fun `skips without Message Content, which a pass would mark done having counted reactions only`() {
        val history = HeldHistory(mutableMapOf("c1" to listOf(message(1, kat)))).apply { readsText = false }

        val outcome = job(history).run()

        assertThat(outcome).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat((outcome as JobOutcome.Skipped).reason).contains("Message Content")
        assertThat(places.held).isEmpty()
        assertThat(queued).isEmpty()
    }

    @Test
    fun `writes each emoji a page shows in a transaction of its own, as a live one is, so the two cannot deadlock`() {
        val log = mutableListOf<String>()
        val repository =
            object : SeenEmojiRepository by HeldSeenEmoji() {
                override fun recordMessage(
                    emojiId: String,
                    messageId: String,
                ): Int = 1.also { log += "count" }
            }
        val logging: PlatformTransactionManager =
            mock {
                on { getTransaction(anyOrNull()) } doAnswer {
                    log += "begin"
                    mock<TransactionStatus>()
                }
                on { commit(any()) } doAnswer { log += "commit" }
            }
        val history = HeldHistory(mutableMapOf("c1" to listOf(message(1, "$kat $kat"), message(2, kat, reactions = listOf(pumpkin)))))
        val provider: ObjectProvider<DiscordHistory> = mock { on { ifAvailable } doReturn history }

        val backfill = SeenEmojiBackfill(provider, places, seenEmojiIndex(repository), logging)
        backfill.startPass()
        backfill.readSome(1)

        assertThat(log.count { it == "count" }).isEqualTo(4)
        assertThat(log.joinToString(" ").split("commit").map { it.split(" ").count { word -> word == "count" } }).allMatch { it <= 1 }
    }

    @Test
    fun `skips without a bot`() {
        assertThat(job(null).run()).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat(job(null).run(DiscordJobs.BackfillSeenEmojiPayload(restart = false))).isInstanceOf(JobOutcome.Skipped::class.java)
        assertThat(queued).isEmpty()
    }

    @Test
    fun `reads nothing where the bot went away between the check and the read`() {
        val gone: ObjectProvider<DiscordHistory> = mock { on { ifAvailable } doReturn null }
        val backfill = SeenEmojiBackfill(gone, places, index, transactions)

        backfill.startPass()
        assertThat(backfill.readSome(1)).isFalse()
        assertThat(places.held).isEmpty()
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
