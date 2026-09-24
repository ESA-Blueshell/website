package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.ClaimedDiscordMembers
import net.blueshell.api.shared.discord.DiscordMemberNamed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.timeout
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher

class DiscordNameFollowerTest {
    private val publisher: ApplicationEventPublisher = mock()

    private fun <T : Any> provided(bean: T?): ObjectProvider<T> = mock { on { ifAvailable } doReturn bean }

    private fun follower(
        source: MemberEvents? = null,
        everyone: List<DiscordMember>? = null,
        linked: Set<String> = emptySet(),
    ): DiscordNameFollower {
        val members: DiscordMemberDirectory = mock { on { everyoneNow() } doReturn everyone }
        return DiscordNameFollower(provided(source), members, provided(ClaimedDiscordMembers { linked }), publisher)
    }

    private fun member(
        id: String,
        name: String,
    ) = DiscordMember(id, name, name.lowercase(), "https://cdn/$id.png")

    @Test
    fun `says a member is called something new`() {
        follower().named("803", "Nelly B")

        verify(publisher).publishEvent(DiscordMemberNamed("803", "Nelly B"))
    }

    @Test
    fun `catches up the linked members only, leaving out whoever left`() {
        val follower = follower(everyone = listOf(member("803", "Nelly B"), member("900", "Stranger")), linked = setOf("803", "804"))

        follower.catchUp()

        verify(publisher).publishEvent(DiscordMemberNamed("803", "Nelly B"))
        verify(publisher, never()).publishEvent(DiscordMemberNamed("900", "Stranger"))
    }

    @Test
    fun `reads nothing of Discord where nobody is linked`() {
        val members: DiscordMemberDirectory = mock()
        DiscordNameFollower(provided(null), members, provided(ClaimedDiscordMembers { emptySet() }), publisher).catchUp()

        verifyNoInteractions(members, publisher)
    }

    @Test
    fun `follows the gateway once started, catching up off its thread on each fresh connection`() {
        var announce: (String, String) -> Unit = { _, _ -> }
        var reconnect: () -> Unit = {}
        val source =
            object : MemberEvents {
                override fun onMemberNamed(listener: (String, String) -> Unit) {
                    announce = listener
                }

                override fun onConnected(listener: () -> Unit) {
                    reconnect = listener
                }
            }
        val follower = follower(source, everyone = listOf(member("803", "Nelly B")), linked = setOf("803"))

        follower.start()
        announce("804", "Anna")
        reconnect()

        verify(publisher).publishEvent(DiscordMemberNamed("804", "Anna"))
        verify(publisher, timeout(2000)).publishEvent(DiscordMemberNamed("803", "Nelly B"))
        assertThat(follower.isRunning).isTrue()
        follower.stop()
        assertThat(follower.isRunning).isFalse()
    }

    @Test
    fun `does not run without a bot`() {
        val follower = follower()

        follower.start()

        assertThat(follower.isRunning).isFalse()
    }
}
