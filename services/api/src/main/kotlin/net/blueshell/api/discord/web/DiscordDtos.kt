package net.blueshell.api.discord.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.discord.domain.DiscordLive
import net.blueshell.api.discord.domain.DiscordMember
import net.blueshell.api.discord.domain.StarboardEntry
import java.time.Instant

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

@Schema(description = "A role in the Discord server an event may ping")
data class DiscordRoleResponse(
    val id: String,
    val name: String,
    @Schema(description = "The role's colour as 0xRRGGBB, where it has one")
    val colour: Int?,
)

@Schema(description = "A text channel a game may live in")
data class DiscordChannelResponse(
    val id: String,
    @Schema(description = "The server the channel is in, which a link into it needs")
    val guildId: String,
    val name: String,
)

@Schema(description = "A picture uploaded to the Discord server, which a description writes as <:name:id>")
data class DiscordEmojiResponse(
    val id: String,
    val name: String,
    @Schema(description = "Moving, written <a:name:id> rather than <:name:id>")
    val animated: Boolean,
)

@Schema(description = "What a description's mentions name: the members, roles and channels the server has")
data class DiscordMentionsResponse(
    val users: List<DiscordNameResponse>,
    val roles: List<DiscordRoleNameResponse>,
    val channels: List<DiscordNameResponse>,
)

@Schema(description = "A member or a channel of the Discord server, by the name the server shows")
data class DiscordNameResponse(
    val id: String,
    val name: String,
)

@Schema(description = "A channel everybody in the Discord server can see, which a description may mention")
data class DiscordMentionChannelResponse(
    val id: String,
    val name: String,
    @Schema(description = "The category the server files the channel under, where it has one")
    val category: String?,
)

@Schema(description = "A role of the Discord server, as a mention shows it")
data class DiscordRoleNameResponse(
    val id: String,
    val name: String,
    @Schema(description = "The role's colour as 0xRRGGBB, where it has one")
    val colour: Int?,
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

@Schema(description = "A message the Discord server starred")
data class StarboardEntryResponse(
    @Schema(description = "The starred message's ID")
    val id: String,
    @Schema(description = "The author's name across Discord")
    val authorName: String,
    @Schema(description = "The author's name in the server, where they set one")
    val authorNickname: String?,
    @Schema(description = "The author's avatar's address, where they have one")
    val avatar: String?,
    @Schema(description = "The message in Discord markdown, with mentions and server emoji; absent for a picture alone")
    val text: String?,
    @Schema(description = "The picture the message carries, where it has one")
    val image: String?,
    val stars: Int,
    @Schema(description = "The name of the channel the message was written in; absent where that channel is members-only")
    val channel: String?,
    @Schema(description = "The address that opens the message in Discord")
    val href: String,
    val postedAt: Instant,
)

internal fun StarboardEntry.toResponse(): StarboardEntryResponse =
    StarboardEntryResponse(
        id = message.id,
        authorName = message.authorName,
        authorNickname = message.authorNickname,
        avatar = message.avatar,
        text = message.text,
        image = message.image,
        stars = message.stars,
        channel = channel,
        href = message.href,
        postedAt = message.postedAt,
    )
