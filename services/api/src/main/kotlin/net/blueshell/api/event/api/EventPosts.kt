package net.blueshell.api.event.api

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.file.api.BlobStore
import net.blueshell.api.file.api.PublicFileUrls
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/** An event as the bot posts it to Discord. [live] is false once it is deleted or no longer approved. */
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
    val signUpDeadline: Instant?,
    val pingedRoleIds: List<String>,
    /** The banner's public path under the api; null without a banner. */
    val bannerPath: String?,
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
    private val blobs: BlobStore,
) {
    @Transactional(readOnly = true)
    fun of(eventId: Long): EventPostData? = events.findByIdIncludingDeleted(eventId)?.asPostData()

    /** Approved events any part of which falls between [from] and [to]. */
    @Transactional(readOnly = true)
    fun approvedOverlapping(
        from: Instant,
        to: Instant,
    ): List<Long> = events.findApprovedIdsOverlapping(from, to)

    /* The widest rendition Discord takes comfortably rather than the master, which can be large. */
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

/* A soft-deleted row is read by the native query, which the entity's restriction does not filter. */
private fun Event.asPostData() =
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
        signUpDeadline = signUpDeadline,
        pingedRoleIds = pingedRoles.map { it.roleId },
        bannerPath = banner?.file?.let(PublicFileUrls::of),
    )
