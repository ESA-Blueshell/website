package net.blueshell.api.event.api

import net.blueshell.api.event.persistence.Event
import net.blueshell.api.event.persistence.EventBannerRepository
import net.blueshell.api.event.persistence.EventRepository
import net.blueshell.api.event.persistence.EventSpecifications
import net.blueshell.api.file.api.FileService
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import net.blueshell.api.event.domain.EventChange
import net.blueshell.api.event.domain.EventQuery
import net.blueshell.api.event.domain.EventSignUpService
import java.time.LocalDateTime

@Service
class EventService @Autowired constructor(
    repository: EventRepository,
    private val eventBannerRepository: EventBannerRepository,
    private val fileService: FileService,
    private val eventSignUpService: EventSignUpService,
    private val trackedEvents: TrackedEventPublisher,
    private val currentUserProvider: CurrentUserProvider
) : BaseModelService<Event, Long, EventRepository>(repository) {
    @Transactional
    override fun create(entity: Event): Event {
        mergeAssociations(entity)
        val saved = super.create(entity)
        publishEventChanged(saved.id!!, EventChange.CREATED)
        return saved
    }

    @Transactional
    override fun update(entity: Event): Event = update(entity, removeExistingSignUps = false)

    /**
     * Sign-ups are deleted only when the caller asks via [removeExistingSignUps]. A form edit
     * never cascades: inferring it from the changed questions destroys responses silently.
     */
    @Transactional
    fun update(entity: Event, removeExistingSignUps: Boolean): Event {
        val previous = findById(entity.id!!).toUpdateSnapshot()

        mergeAssociations(entity)
        val saved = super.update(entity)

        maybeDeleteReplacedBannerFile(previous.bannerFileId, saved.banner?.file?.id)
        if (removeExistingSignUps) {
            clearSignUpsForEvent(saved.id!!)
        }

        publishEventChanged(saved.id!!, EventChange.UPDATED)
        return saved
    }

    /**
     * Persists calendar linkage changes without publishing EventChanged.
     *
     * Calendar jobs update googleId as part of synchronization. Emitting EventChanged here
     * would re-enqueue calendar jobs and can create scheduling loops.
     */
    @Transactional
    fun updateCalendarLink(entity: Event, googleId: String?): Event {
        if (entity.googleId == googleId) {
            return entity
        }

        entity.googleId = googleId
        return super.update(entity)
    }

    @Transactional
    override fun delete(entity: Event) {
        val eventId = entity.id!!
        super.delete(entity)
        publishEventChanged(eventId, EventChange.DELETED)
    }

    @Transactional
    override fun deleteById(id: Long) {
        super.deleteById(id)
        publishEventChanged(id, EventChange.DELETED)
    }

    /**
     * The events a caller may see, with their promo art ready to be drawn.
     *
     * The graph fetches the banner and its file; the widths are a collection, and putting a
     * collection in the graph of a paged query makes Hibernate page in memory. So they are
     * read here instead, inside this transaction, where `default_batch_fetch_size` collects
     * the whole page in one query. Touching them is the read: `enable_lazy_load_no_trans` is
     * on, so without it each width would be fetched later in a session of its own — one query
     * per event rather than one per page.
     */
    @Transactional(readOnly = true)
    fun findByFilter(pageable: Pageable, filter: EventQuery): Page<Event> {
        val spec = EventSpecifications.fromFilter(filter, currentUserProvider.currentUser())
        val page = repository.findAll(spec, pageable)
        page.content.forEach { it.banner?.file?.renditions?.size }
        return page
    }

    /**
     * How many events the caller may see between two moments, counted rather than paged through.
     *
     * Takes the window rather than an [EventQuery] because the query is this module's own and
     * a caller outside it reaches only the published surface. The same specification the list
     * is built from, so the number never describes events the caller could not open: a visitor
     * counts the approved ones, a board member counts more.
     */
    @Transactional(readOnly = true)
    fun countBetween(from: LocalDateTime, to: LocalDateTime): Long {
        val query = EventQuery(from = from, to = to)
        val spec = EventSpecifications.fromFilter(query, currentUserProvider.currentUser())
        return repository.count(spec)
    }

    @Transactional(readOnly = true)
    fun findByIdIncludingDeletedOrNull(id: Long): Event? {
        return repository.findByIdIncludingDeleted(id)
    }

    private fun maybeDeleteReplacedBannerFile(previousFileId: Long?, updatedFileId: Long?) {
        if (previousFileId == null || previousFileId == updatedFileId) {
            return
        }
        deleteBannerFileIfOrphaned(previousFileId)
    }

    private fun deleteBannerFileIfOrphaned(fileId: Long) {
        if (eventBannerRepository.countByIdFileId(fileId) == 0L && fileService.existsById(fileId)) {
            fileService.deleteById(fileId)
        }
    }

    private fun clearSignUpsForEvent(eventId: Long) {
        val signUps = eventSignUpService.findByEventId(eventId).toSet()
        if (signUps.isNotEmpty()) {
            eventSignUpService.deleteAll(signUps)
        }
    }

    private fun mergeAssociations(event: Event) {
        event.banner?.let { banner ->
            banner.event = event
            val fileId = requireNotNull(banner.file.id) { "Event banner file ID is required" }
            banner.file = fileService.findById(fileId)
            banner.id.fileId = fileId
        }
        event.signUpForm?.let { survey ->
            survey.questions.forEach { question ->
                question.survey = survey
            }
        }
    }

    private fun publishEventChanged(eventId: Long, changeType: EventChange) {
        trackedEvents.publish { actor ->
            EventChanged(eventId, changeType, actor = actor)
        }
    }

    private data class EventUpdateSnapshot(
        val bannerFileId: Long?,
    )

    private fun Event.toUpdateSnapshot(): EventUpdateSnapshot {
        return EventUpdateSnapshot(
            bannerFileId = banner?.file?.id,
        )
    }
}
