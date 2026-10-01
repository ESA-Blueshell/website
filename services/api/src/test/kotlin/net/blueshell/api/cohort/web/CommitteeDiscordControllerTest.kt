package net.blueshell.api.cohort.web

import net.blueshell.api.cohort.domain.CommitteeDiscord
import net.blueshell.api.cohort.domain.CommitteeDiscordChoice
import net.blueshell.api.cohort.domain.CommitteeDiscordState
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CommitteeDiscordControllerTest {
    @Test
    fun `reads and sets a committee's role and channels through the service`() {
        val discord: CommitteeDiscord = mock()
        val state = CommitteeDiscordState(true, "900", "Sitecie", emptyList())
        whenever(discord.read(7)).thenReturn(state)
        whenever(discord.apply(7, CommitteeDiscordChoice(null, true, listOf("1"), "sitecie"))).thenReturn(state)
        val controller = CommitteeDiscordController(discord)

        assertThat(controller.findCommitteeDiscord(7)).isSameAs(state)
        assertThat(controller.setCommitteeDiscord(7, CommitteeDiscordRequest(createRole = true, channelIds = listOf("1"), createChannel = "sitecie")))
            .isSameAs(state)
        assertThat(CommitteeDiscordRequest().channelIds).isEmpty()
    }
}
