package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.discord.defaultAvatarOf
import org.springframework.stereotype.Component

/** One seat as a committee's public page shows it: never the person's full name, only their Discord or username. */
data class CommitteeSeat(
    /** The name the server shows them by, or their username where Discord is not linked. */
    val name: String,
    val avatar: String?,
    /** Whether the name is their Discord name. */
    val discord: Boolean,
    val role: String?,
)

/**
 * A committee's seats by Discord. A linked member the bot cannot see, or any member while there is
 * no bot, keeps the name they last had on the server and Discord's default avatar for them.
 */
@Component
class CommitteeSeats(
    private val faces: DiscordFaces,
) {
    fun of(committee: Committee): List<CommitteeSeat> {
        val linked = committee.members.mapNotNull { it.user.discordId }
        val seen = faces.of(linked)
        return committee.members.map { member ->
            val id = member.user.discordId
            val face = id?.let(seen::get)
            val discordName = face?.name ?: id?.let { member.user.discord?.ifBlank { null } }
            if (id == null || discordName == null) {
                CommitteeSeat(name = member.user.username, avatar = null, discord = false, role = member.role)
            } else {
                CommitteeSeat(name = discordName, avatar = face?.avatar ?: defaultAvatarOf(id), discord = true, role = member.role)
            }
        }
    }
}
