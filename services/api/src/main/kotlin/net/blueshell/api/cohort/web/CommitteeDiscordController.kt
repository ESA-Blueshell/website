package net.blueshell.api.cohort.web

import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.tags.Tag
import net.blueshell.api.cohort.domain.CommitteeDiscord
import net.blueshell.api.cohort.domain.CommitteeDiscordChoice
import net.blueshell.api.cohort.domain.CommitteeDiscordState
import net.blueshell.api.security.BoardOnly
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@Schema(name = "CommitteeDiscordRequest", description = "The role a committee's seats hold, and the channels it opens.")
data class CommitteeDiscordRequest(
    @param:Schema(description = "An existing role to link, where the committee has none yet")
    val roleId: String? = null,
    @param:Schema(description = "Make a new role, where the committee has none yet and no role is named")
    val createRole: Boolean = false,
    @param:Schema(description = "Every channel the role opens; one left out is closed to it")
    val channelIds: List<String> = emptyList(),
    @param:Schema(description = "A new private channel to make for the role, by name")
    val createChannel: String? = null,
)

@RestController
@Tag(name = "Committee Discord", description = "A committee's role and private channels")
@BoardOnly
class CommitteeDiscordController(
    private val discord: CommitteeDiscord,
) {
    @GetMapping("/management/committees/{id}/discord")
    fun findCommitteeDiscord(
        @PathVariable id: Long,
    ): CommitteeDiscordState = discord.read(id)

    @PutMapping("/management/committees/{id}/discord")
    fun setCommitteeDiscord(
        @PathVariable id: Long,
        @RequestBody request: CommitteeDiscordRequest,
    ): CommitteeDiscordState =
        discord.apply(id, CommitteeDiscordChoice(request.roleId, request.createRole, request.channelIds, request.createChannel))
}
