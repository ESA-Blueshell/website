package net.blueshell.api.committee.domain

import net.blueshell.api.committee.persistence.Committee
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.discord.defaultAvatarOf
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Component

/** One seat as a committee's public page shows it: never the person's name, only their Discord. */
data class CommitteeSeat(
    /** Their Discord username, or nothing for a member who has not linked Discord. */
    val discordTag: String?,
    val avatar: String?,
    val role: String?,
)

/**
 * A committee's seats by Discord. A linked member the bot cannot see, or any member while there is
 * no bot, keeps the name they last had on the server and Discord's default avatar for them.
 */
@Component
class CommitteeSeats(
    private val faces: ObjectProvider<DiscordFaces>,
) {
    fun of(committee: Committee): List<CommitteeSeat> {
        val linked = committee.members.mapNotNull { it.user.discordId }
        val seen = faces.ifAvailable?.of(linked).orEmpty()
        return committee.members.map { member ->
            val id = member.user.discordId
            val face = id?.let(seen::get)
            CommitteeSeat(
                discordTag = face?.tag ?: id?.let { member.user.discord },
                avatar = face?.avatar ?: id?.let(::defaultAvatarOf),
                role = member.role,
            )
        }
    }
}
