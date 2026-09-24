package net.blueshell.api.sync.domain

import net.blueshell.api.sync.api.ExternalIdMappingService
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/** The three things the bot keeps in the server for an event, under the glossary's names. */
enum class DiscordArtefact(
    val system: String,
) {
    INFO_POST("DISCORD_EVENTS_INFO"),
    CALENDAR_POST("DISCORD_EVENTS_CALENDAR"),
    DISCORD_EVENT("DISCORD_EVENT"),
}

/** One artefact as recorded: its ID in Discord, and a fingerprint of the content it carries. */
data class RecordedArtefact(
    val externalId: String,
    val fingerprint: Long,
)

/** What the bot has put in the server for each event, and the claims that keep it from doing so twice. */
interface PostLedger {
    /** Null where nothing is out there, including a claim still being carried out. */
    fun find(
        eventId: Long,
        artefact: DiscordArtefact,
    ): RecordedArtefact?

    fun claim(
        eventId: Long,
        artefact: DiscordArtefact,
        now: Instant,
    ): Boolean

    fun record(
        eventId: Long,
        artefact: DiscordArtefact,
        externalId: String,
        fingerprint: Long,
    )

    fun release(
        eventId: Long,
        artefact: DiscordArtefact,
    )
}

/** The ledger in `external_id_mapping`, beside the event's other external IDs. */
@Component
class MappingPostLedger(
    private val mappings: ExternalIdMappingService,
) : PostLedger {
    override fun find(
        eventId: Long,
        artefact: DiscordArtefact,
    ): RecordedArtefact? {
        val mapping = mappings.find(AGGREGATE, eventId, artefact.system) ?: return null
        val id = mapping.externalId ?: return null
        return RecordedArtefact(id, mapping.syncedVersion ?: 0)
    }

    override fun claim(
        eventId: Long,
        artefact: DiscordArtefact,
        now: Instant,
    ) = mappings.claim(AGGREGATE, eventId, artefact.system, now.minus(CLAIM_EXPIRES))

    override fun record(
        eventId: Long,
        artefact: DiscordArtefact,
        externalId: String,
        fingerprint: Long,
    ) = mappings.record(AGGREGATE, eventId, artefact.system, externalId, fingerprint)

    override fun release(
        eventId: Long,
        artefact: DiscordArtefact,
    ) = mappings.release(AGGREGATE, eventId, artefact.system)

    private companion object {
        const val AGGREGATE = "EVENT"

        /* Far longer than one post takes; a claim this old was left by a run that died. */
        val CLAIM_EXPIRES: Duration = Duration.ofMinutes(15)
    }
}
