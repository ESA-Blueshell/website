package net.blueshell.api.event.api

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSignUpRepository
import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * An event as the bot posts it to Discord. [live] is false once it is deleted or no longer approved;
 * [frozen] is true while it awaits re-approval, when what is out stays as last approved.
 */
data class EventPostData(
    val id: Long,
    val live: Boolean,
    val title: String,
    val description: String?,
    val location: String?,
    val startTime: Instant,
    val endTime: Instant,
    val memberPrice: Double?,
    val publicPrice: Double?,
    val membersOnly: Boolean,
    /** Whether people sign up for it on the site. */
    val signUp: Boolean,
    val signUpCount: Long,
    /** Null for no limit. */
    val signUpLimit: Int?,
    val signUpDeadline: Instant?,
    val pingedRoleIds: List<String>,
    /** The Discord IDs of those signed up whose account has Discord linked, first sign-up first. */
    val goingDiscordIds: List<String> = emptyList(),
    /** The banner's public path, new with every banner; null without one. */
    val bannerPath: String?,
    val frozen: Boolean = false,
    /** When the events-info post goes out, as the board chose on approving. */
    val announceAt: Instant? = null,
)

/** An event banner's bytes, for a Discord event's cover, which Discord takes as data rather than a link. */
data class EventBannerImage(
    val mediaType: String,
    val bytes: ByteArray,
)

/**
 * The events the bot posts to Discord, read through the published surface so the posting side
 * needs no event entity. Deleted events are still read, so their posts can be taken down.
 */
@Service
class EventPosts(
    private val events: EventRepository,
    private val signUps: EventSignUpRepository,
    private val blobs: BlobStore,
) {
    @Transactional(readOnly = true)
    fun of(eventId: Long): EventPostData? =
        events.findByIdIncludingDeleted(eventId)?.let { event ->
            event.asPostData(if (event.signUp) signUps.findLinkedDiscordIds(eventId) else emptyList())
        }

    /** Events whose Discord things the bot keeps, approved or awaiting re-approval, that end at or after [from]. */
    @Transactional(readOnly = true)
    fun keptEndingFrom(from: Instant): List<Long> = events.findKeptIdsEndingFrom(from)

    // The widest rendition Discord takes comfortably rather than the master, which can be large.
    @Transactional(readOnly = true)
    fun bannerOf(eventId: Long): EventBannerImage? {
        val master = events.findByIdIncludingDeleted(eventId)?.banner?.file ?: return null
        val file = master.renditions.lastOrNull { (it.renditionWidth ?: 0) <= COVER_WIDTH } ?: master
        return EventBannerImage(file.mediaType, blobs.open(file.path).use { it.readAllBytes() })
    }

    private companion object {
        const val COVER_WIDTH = 1600
    }
}

// A soft-deleted row is read by the native query, which the entity's restriction does not filter.
private fun Event.asPostData(goingDiscordIds: List<String>) =
    EventPostData(
        id = id!!,
        live = approved && !isSoftDeleted,
        title = title,
        description = description,
        location = location,
        startTime = startTime,
        endTime = endTime,
        memberPrice = memberPrice,
        publicPrice = publicPrice,
        membersOnly = membersOnly,
        signUp = signUp,
        signUpCount = signUpCount,
        signUpLimit = signUpLimit,
        signUpDeadline = signUpDeadline,
        pingedRoleIds = pingedRoles.map { it.roleId },
        goingDiscordIds = goingDiscordIds,
        bannerPath = banner?.file?.let(PublicFileUrls::of),
        frozen = awaitingReapproval && !isSoftDeleted,
        announceAt = announceAt,
    )
