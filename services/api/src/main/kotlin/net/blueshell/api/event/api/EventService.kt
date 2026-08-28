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
     * Form edits no longer cascade-delete sign-ups: that only happens when the caller
     * explicitly asks for it via [removeExistingSignUps]. The previous heuristic (dirty
     * questions / changed keys) silently destroyed responses on any non-trivial edit.
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

    fun findByFilter(pageable: Pageable, filter: EventQuery): Page<Event> {
        val spec = EventSpecifications.fromFilter(filter, currentUserProvider.currentUser())
        return repository.findAll(spec, pageable)
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
