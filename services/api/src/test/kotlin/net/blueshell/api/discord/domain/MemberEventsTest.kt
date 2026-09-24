package net.blueshell.api.discord.domain

import net.blueshell.clients.discord.api.DiscordApi
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.RawGatewayEvent
import net.dv8tion.jda.api.events.guild.GuildReadyEvent
import net.dv8tion.jda.api.utils.data.DataObject
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class MemberEventsTest {
    private fun payload(json: String) = DataObject.fromJson(json)

    @Test
    fun `reads the member a member event is about, by the name the server shows`() {
        val user = """"user": {"id": "803", "username": "nelly", "global_name": "Nelly"}"""

        assertThat(namedIn(payload("""{"guild_id": "324", "nick": "Nelly B", $user}"""), "324")).isEqualTo("803" to "Nelly B")
        assertThat(namedIn(payload("""{"guild_id": "324", "nick": null, $user}"""), "324")).isEqualTo("803" to "Nelly")
        assertThat(namedIn(payload("""{"guild_id": "324", "user": {"id": "803", "username": "nelly"}}"""), "324"))
            .isEqualTo("803" to "nelly")
    }

    @Test
    fun `ignores another server's members, and an event without a member`() {
        assertThat(namedIn(payload("""{"guild_id": "999", "user": {"id": "803", "username": "nelly"}}"""), "324")).isNull()
        assertThat(namedIn(payload("""{"guild_id": "324"}"""), "324")).isNull()
        assertThat(namedIn(payload("""{"guild_id": "324", "user": {"username": "nelly"}}"""), "324")).isNull()
        assertThat(namedIn(payload("""{"guild_id": "324", "user": {"id": "803"}}"""), "324")).isNull()
    }

    @Test
    fun `passes on member changes and fresh connections, and nothing else`() {
        val source = JdaVoiceServerSource("token", "324", mock<DiscordApi>())
        val heard = mutableListOf<String>()
        source.onMemberNamed { id, name -> heard += "$id=$name" }
        source.onConnected { heard += "connected" }
        val update: RawGatewayEvent =
            mock {
                on { type } doReturn "GUILD_MEMBER_UPDATE"
                on { payload } doReturn payload("""{"guild_id": "324", "user": {"id": "803", "username": "nelly"}}""")
            }
        val typing: RawGatewayEvent = mock { on { type } doReturn "TYPING_START" }

        source.relay.onEvent(update)
        source.relay.onEvent(typing)
        source.relay.onEvent(GuildReadyEvent(mock<JDA>(), 1, mock()))

        assertThat(heard).containsExactly("803=nelly", "connected")
    }
}
