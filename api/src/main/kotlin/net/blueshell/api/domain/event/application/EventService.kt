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
        trackedEvents.publish { actor ->
            EventChanged(
                saved.id!!,
                EventChange.CREATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun update(entity: Event): Event {
        val previous = findById(entity.id!!)
        val previousBannerFileId = previous.banner?.file?.id
        val previousNonDescriptionQuestions = previous.nonDescriptionQuestionKeys()
        val hadSignUpForm = previous.signUpForm != null

        mergeAssociations(entity)
        val saved = super.update(entity)

        val updatedBannerFileId = saved.banner?.file?.id
        if (previousBannerFileId != null && previousBannerFileId != updatedBannerFileId) {
            deleteBannerFileIfOrphaned(previousBannerFileId)
        }
        if (shouldInvalidateSignUps(hadSignUpForm, previousNonDescriptionQuestions, saved)) {
            clearSignUpsForEvent(saved.id!!)
        }

        trackedEvents.publish { actor ->
            EventChanged(
                saved.id!!,
                EventChange.UPDATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun delete(entity: Event) {
        val eventId = entity.id!!
        super.delete(entity)
        trackedEvents.publish { actor ->
            EventChanged(
                eventId,
                EventChange.DELETED,
                actor = actor
            )
        }
    }

    @Transactional
    override fun deleteById(id: Long) {
        super.deleteById(id)
        trackedEvents.publish { actor ->
            EventChanged(
                id,
                EventChange.DELETED,
                actor = actor
            )
        }
    }

    fun findByFilter(pageable: Pageable, filter: EventQuery): Page<Event> {
        val spec = EventSpecifications.fromFilter(filter, currentUserProvider.currentUser())
        return repository.findAll(spec, pageable)
    }

    @Transactional
    fun deleteBannerFileIfOrphaned(fileId: Long) {
        if (eventBannerRepository.countByIdFileId(fileId) == 0L && fileService.existsById(fileId)) {
            fileService.deleteById(fileId)
        }
    }

    @Transactional
    fun clearSignUpsForEvent(eventId: Long) {
        val signUps = eventSignUpService.findByEventId(eventId).toSet()
        if (signUps.isNotEmpty()) {
            eventSignUpService.deleteAll(signUps)
        }
    }

    private fun mergeAssociations(event: Event) {
        // Set parent reference for one-to-one owned relationship
        event.banner?.let { banner ->
            banner.event = event
            val fileId = banner.file.id ?: banner.id.fileId
            // Replace transient file entity with managed reference inside this transaction
            fileId?.let {
                banner.file = fileService.findById(it)
                banner.id.fileId = fileId
            }
        }
        // Set parent references for one-to-many nested relationship
        event.signUpForm?.let { survey ->
            survey.questions.forEach { question ->
                question.survey = survey
            }
        }
    }

    private fun shouldInvalidateSignUps(
        hadSignUpForm: Boolean,
        previousNonDescriptionQuestions: Set<QuestionKey>,
        saved: Event
    ): Boolean {
        val currentSignUpForm = saved.signUpForm ?: return false
        if (!hadSignUpForm) {
            return true
        }

        val currentNonDescriptionQuestions = saved.nonDescriptionQuestionKeys()
        if (currentNonDescriptionQuestions.any { it !in previousNonDescriptionQuestions }) {
            return true
        }

        return currentSignUpForm.questions.any { question ->
            question.type != QuestionType.DESCRIPTION && question.dirty
        }
    }

    private fun Event.nonDescriptionQuestionKeys(): Set<QuestionKey> {
        return signUpForm?.questions
            ?.filter { it.type != QuestionType.DESCRIPTION }
            ?.map { QuestionKey(idx = it.idx, type = it.type) }
            ?.toSet()
            ?: emptySet()
    }

    private data class QuestionKey(
        val idx: Long,
        val type: QuestionType
    )
}
