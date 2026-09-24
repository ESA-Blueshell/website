package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordMember

@Schema(description = "The association's Discord server as the site shows it: counts and the voice rooms with who is in them")
data class DiscordLiveResponse(
    @Schema(description = "The server's name as Discord has it")
    val server: String,
    @Schema(description = "How many members are online, where Discord says")
    val online: Int?,
    @Schema(description = "How many members the server has, where Discord says")
    val members: Int?,
    @Schema(description = "The voice rooms everybody can see, in Discord's order")
    val rooms: List<DiscordVoiceRoomResponse>,
)

@Schema(description = "A voice room everybody can see, with who is in it")
data class DiscordVoiceRoomResponse(
    val id: String,
    val name: String,
    @Schema(description = "Everybody can see the room, but only some may join it")
    val locked: Boolean,
    @Schema(description = "The address that opens the room in Discord")
    val href: String,
    val people: List<DiscordVoicePersonResponse>,
)

@Schema(description = "The voice rooms the viewer's own Discord member may join")
data class DiscordViewerRoomsResponse(
    @Schema(description = "Whether the viewer's account is linked to a member of the server")
    val linked: Boolean,
    @Schema(description = "The IDs of the voice rooms that member may join")
    val joinable: List<String>,
)

@Schema(description = "A member of the Discord server, as a picker shows them")
data class DiscordMemberResponse(
    @Schema(description = "Their Discord user ID, which never changes")
    val id: String,
    @Schema(description = "The name the server shows: their nickname there, else their display name, else their username")
    val name: String,
    val username: String,
    @Schema(description = "Their avatar's address")
    val avatar: String,
)

fun DiscordMember.toResponse() = DiscordMemberResponse(id = id, name = name, username = username, avatar = avatar)

@Schema(description = "Somebody in a voice room")
data class DiscordVoicePersonResponse(
    val name: String,
    @Schema(description = "Their avatar's address, where they have one")
    val avatar: String?,
)

internal fun DiscordLive.toResponse(): DiscordLiveResponse =
    DiscordLiveResponse(
        server = server,
        online = online,
        members = members,
        rooms =
            rooms.map { (room, href) ->
                DiscordVoiceRoomResponse(
                    id = room.id,
                    name = room.name,
                    locked = room.locked,
                    href = href,
                    people = room.people.map { DiscordVoicePersonResponse(it.name, it.avatar) },
                )
            },
    )
