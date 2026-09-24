package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.DiscordLinks
import net.blueshell.api.shared.security.CurrentUserProvider
import org.springframework.beans.factory.ObjectProvider
import org.springframework.stereotype.Service

/** What the band may unlock for the person looking: whether their account has a member, and what that member may join. */
data class ViewerRooms(
    val linked: Boolean,
    val joinable: Set<String>,
)

/**
 * The voice rooms the logged-in viewer's own Discord member may join. Somebody logged out, or with
 * no member linked, gets nothing unlocked, which is what the public feed already shows them.
 */
@Service
class ViewerRoomService(
    private val viewer: CurrentUserProvider,
    private val links: ObjectProvider<DiscordLinks>,
    private val access: ObjectProvider<RoomAccess>,
) {
    /** Null without the gateway, so the band keeps the public locks. */
    fun rooms(): ViewerRooms? {
        val rooms = access.ifAvailable ?: return null
        val memberId = viewer.currentUser()?.let { links.ifAvailable?.discordIdOf(it.id) } ?: return ViewerRooms(false, emptySet())
        return rooms.joinableBy(memberId)?.let { ViewerRooms(true, it) }
    }
}
