package net.blueshell.api.discord.domain

import net.blueshell.api.discord.persistence.SeenEmojiBackfillPlaceRepository
import net.blueshell.api.testsupport.ServiceTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

/** The backfill against the real tables, inside one outer transaction as the job runs it. */
class SeenEmojiBackfillIT : ServiceTestSupport() {
    @Autowired
    private lateinit var places: SeenEmojiBackfillPlaceRepository

    @Autowired
    private lateinit var index: SeenEmojiIndex

    private val noon = Instant.parse("2026-10-24T12:00:00Z")

    // Three pages in one place: each names the oldest message it reached.
    private val history =
        object : DiscordHistory {
            val asked = mutableListOf<String?>()

            override fun readsText() = true

            override fun places() = listOf("8801")

            override fun page(
                channelId: String,
                before: String?,
            ): HistoryPage {
                asked += before
                val oldest =
                    when (before) {
                        null -> "300"
                        "300" -> "200"
                        else -> "100"
                    }
                return HistoryPage(listOf(EmojiSighting("7701", "backfilled", false, oldest, noon)), oldest, last = oldest == "100")
            }
        }

    private fun backfill(): SeenEmojiBackfill {
        val provider: ObjectProvider<DiscordHistory> = mock { on { ifAvailable } doReturn history }
        return SeenEmojiBackfill(provider, places, index, transactionManager)
    }

    @Test
    fun `moves through a place page by page within one run, and a new pass reads it again counting nothing twice`() {
        transactionTemplate.executeWithoutResult {
            backfill().startPass()
            assertThat(backfill().readSome(2)).isTrue()
        }
        transactionTemplate.executeWithoutResult { assertThat(backfill().readSome(5)).isFalse() }
        assertThat(history.asked).containsExactly(null, "300", "200")

        transactionTemplate.executeWithoutResult {
            backfill().startPass()
            backfill().readSome(5)
        }

        assertThat(index.seen().single { it.id == "7701" }.uses).isEqualTo(3L)
        assertThat(history.asked).containsExactly(null, "300", "200", null, "300", "200")
    }
}
