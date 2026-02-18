package net.blueshell.api.domain.event.application

import net.blueshell.api.domain.event.application.event.EventChange
import net.blueshell.api.domain.event.application.event.EventChanged
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.application.query.EventQuery
import net.blueshell.api.domain.event.persistence.repository.EventBannerRepository
import net.blueshell.api.domain.event.persistence.repository.EventRepository
import net.blueshell.api.domain.event.persistence.spec.EventSpecifications
import net.blueshell.api.domain.file.application.FileService
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    override fun update(entity: Event): Event {
        val previous = findById(entity.id!!).toUpdateSnapshot()

        mergeAssociations(entity)
        val saved = super.update(entity)

        maybeDeleteReplacedBannerFile(previous.bannerFileId, saved.banner?.file?.id)
        if (shouldInvalidateSignUps(previous, saved)) {
            clearSignUpsForEvent(saved.id!!)
        }

        publishEventChanged(saved.id!!, EventChange.UPDATED)
        return saved
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

    private fun shouldInvalidateSignUps(previous: EventUpdateSnapshot, updated: Event): Boolean {
        val updatedForm = updated.signUpForm ?: return false
        if (!previous.hadSignUpForm) {
            return true
        }

        if ((updated.nonDescriptionQuestionKeys() - previous.nonDescriptionQuestionKeys).isNotEmpty()) {
            return true
        }

        return updatedForm.questions.any { question ->
            question.type != QuestionType.DESCRIPTION && question.dirty
        }
    }

    private fun Event.nonDescriptionQuestionKeys(): Set<NonDescriptionQuestionKey> {
        return signUpForm?.questions
            ?.filter { it.type != QuestionType.DESCRIPTION }
            ?.map { NonDescriptionQuestionKey(it.idx, it.type) }
            ?.toSet()
            ?: emptySet()
    }

    private fun publishEventChanged(eventId: Long, changeType: EventChange) {
        trackedEvents.publish { actor ->
            EventChanged(eventId, changeType, actor = actor)
        }
    }

    private data class EventUpdateSnapshot(
        val bannerFileId: Long?,
        val hadSignUpForm: Boolean,
        val nonDescriptionQuestionKeys: Set<NonDescriptionQuestionKey>
    )

    private data class NonDescriptionQuestionKey(
        val idx: Long,
        val type: QuestionType
    )

    private fun Event.toUpdateSnapshot(): EventUpdateSnapshot {
        return EventUpdateSnapshot(
            bannerFileId = banner?.file?.id,
            hadSignUpForm = signUpForm != null,
            nonDescriptionQuestionKeys = nonDescriptionQuestionKeys(),
        )
    }
}
