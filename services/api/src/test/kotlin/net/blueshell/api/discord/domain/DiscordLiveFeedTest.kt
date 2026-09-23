package net.blueshell.api.discord.domain

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.ObjectProvider
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DiscordLiveFeedTest {
    private val quiet = DiscordLive(server = "Blueshell", online = 269, members = 1199, rooms = emptyList())
    private val busier = quiet.copy(online = 270)

    private fun <T : Any> provided(bean: T?): ObjectProvider<T> = mock { on { ifAvailable } doReturn bean }

    private fun feed(
        vararg answers: DiscordLive?,
        source: VoiceServerSource? = null,
    ): DiscordLiveFeed {
        val queue = ArrayDeque(answers.toList())
        val live: DiscordLiveService = mock()
        whenever(live.live()).thenAnswer { if (queue.size > 1) queue.removeFirst() else queue.firstOrNull() }
        return DiscordLiveFeed(live, provided(source))
    }

    @Test
    fun `hands subscribers a server only when it differs from the last`() {
        val feed = feed(quiet, quiet, busier)
        val heard = mutableListOf<DiscordLive?>()
        feed.subscribe { heard += it }

        repeat(3) { feed.refresh() }

        assertThat(heard).containsExactly(quiet, busier)
        assertThat(feed.current()).isEqualTo(busier)
    }

    @Test
    fun `tells subscribers when the bot drops out, and stops telling one that left`() {
        val feed = feed(quiet, null, quiet)
        val heard = mutableListOf<DiscordLive?>()
        val leave = feed.subscribe { heard += it }

        feed.refresh()
        feed.refresh()
        leave()
        feed.refresh()

        assertThat(heard).containsExactly(quiet, null)
    }

    @Test
    fun `keeps telling the others when one subscriber throws`() {
        val feed = feed(quiet)
        val heard = AtomicInteger()
        feed.subscribe { error("gone") }
        feed.subscribe { heard.incrementAndGet() }

        feed.refresh()

        assertThat(heard.get()).isOne()
    }

    @Test
    fun `settles a burst of gateway changes into one look`() {
        var announce: () -> Unit = {}
        val source =
            object : VoiceServerSource {
                override fun server(): VoiceServer? = null

                override fun onChange(listener: () -> Unit) {
                    announce = listener
                }
            }
        val feed = feed(quiet, busier, source = source)
        val heard = CountDownLatch(1)
        val looks = AtomicInteger()
        feed.subscribe {
            looks.incrementAndGet()
            heard.countDown()
        }
        feed.start()
        try {
            repeat(50) { announce() }
            assertThat(heard.await(2, TimeUnit.SECONDS)).isTrue()
            Thread.sleep(DiscordLiveFeed.SETTLE.toMillis() * 2)
            assertThat(looks.get()).isOne()
        } finally {
            feed.stop()
        }
    }

    @Test
    fun `does not run without a bot`() {
        val feed = feed(quiet)
        feed.start()

        assertThat(feed.isRunning).isFalse()
        feed.changed()
    }
}
