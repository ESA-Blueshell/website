package net.blueshell.api.discord.domain

import net.blueshell.api.shared.discord.ClaimedDiscordMembers
import net.blueshell.api.shared.discord.DiscordMemberNamed
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.SmartLifecycle
import org.springframework.stereotype.Service
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Says whenever a member of the server is called something new, so the account linked to them
 * follows. Discord pushes every nickname, display name and username change to the bot; each time
 * the gateway has the server afresh, the linked members are read again, since changes made while
 * it was away were never pushed. A member who left keeps their last name.
 *
 * The catch-up reads Discord, which the gateway's own thread must not wait on, so it runs on one of
 * its own.
 */
@Service
class DiscordNameFollower(
    private val events: ObjectProvider<MemberEvents>,
    private val members: DiscordMemberDirectory,
    private val claimed: ObjectProvider<ClaimedDiscordMembers>,
    private val publisher: ApplicationEventPublisher,
) : SmartLifecycle {
    @Volatile private var executor: ExecutorService? = null

    internal fun named(
        discordId: String,
        name: String,
    ) = publisher.publishEvent(DiscordMemberNamed(discordId, name))

    internal fun catchUp() {
        val linked = claimed.ifAvailable?.claimedIds().orEmpty()
        if (linked.isEmpty()) return
        members
            .everyoneNow()
            ?.filter { it.id in linked }
            ?.forEach { named(it.id, it.name) }
    }

    override fun start() {
        val source = events.ifAvailable ?: return
        val running = Executors.newSingleThreadExecutor { Thread(it, "discord-names").apply { isDaemon = true } }
        executor = running
        source.onMemberNamed(::named)
        source.onConnected {
            running.execute { runCatching(::catchUp).onFailure { log.warn("Discord names could not be caught up", it) } }
        }
    }

    override fun stop() {
        executor?.shutdownNow()
        executor = null
    }

    override fun isRunning(): Boolean = executor != null

    private companion object {
        val log = LoggerFactory.getLogger(DiscordNameFollower::class.java)
    }
}
