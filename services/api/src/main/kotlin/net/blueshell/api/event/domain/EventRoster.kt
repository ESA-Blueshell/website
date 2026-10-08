package net.blueshell.api.event.domain

import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.shared.discord.DiscordFaces
import net.blueshell.api.shared.discord.defaultAvatarOf
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** One person on an event's roster: the name the server shows them by and their picture, or their username and none. */
data class RosterPerson(
    val name: String,
    val avatar: String?,
    /** Whether the name is their Discord name, drawn as Discord writes a mention. */
    val discord: Boolean,
)

/** Who signed up for an event, as its page shows it: the people by name, and the guests counted. */
data class Roster(
    val people: List<RosterPerson>,
    val guests: Int,
)

/**
 * An event's roster for its page. Nothing about a person leaves but the name the site shows them by
 * and a picture: never an account id, a full name, an email or an answer. A guest typed their own
 * handle and has no linked account, so a guest is counted and never named.
 */
@Component
class EventRoster(
    private val signUps: EventSignUpRepository,
    private val faces: DiscordFaces,
) {
    @Transactional(readOnly = true)
    fun of(eventId: Long): Roster {
        val rows = signUps.findRoster(eventId)
        val accounts = rows.filter { it.username != null }
        // A member the bot cannot see, or any member while there is no bot, keeps the name they last had on the server.
        val seen = faces.of(accounts.mapNotNull { it.discordId?.ifBlank { null } })
        val people =
            accounts.map { row ->
                val id = row.discordId?.ifBlank { null }
                val face = id?.let(seen::get)
                val discordName = face?.name ?: id?.let { row.discord?.ifBlank { null } }
                if (id == null || discordName == null) {
                    RosterPerson(requireNotNull(row.username), avatar = null, discord = false)
                } else {
                    RosterPerson(discordName, face?.avatar ?: defaultAvatarOf(id), discord = true)
                }
            }
        return Roster(people, guests = rows.size - accounts.size)
    }
}
