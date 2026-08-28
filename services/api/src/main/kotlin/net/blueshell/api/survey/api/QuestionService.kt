package net.blueshell.api.survey.api

import net.blueshell.api.survey.domain.QuestionChange
import net.blueshell.api.survey.domain.QuestionChanged
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.QuestionRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionService @Autowired constructor(
    repository: QuestionRepository,
    private val trackedEvents: TrackedEventPublisher
) : BaseModelService<Question, Long, QuestionRepository>(repository) {
    @Transactional
    override fun create(entity: Question): Question {
        val saved = super.create(entity)
        trackedEvents.publish { actor ->
            QuestionChanged(
                questionId = saved.id!!,
                surveyId = saved.surveyId,
                type = saved.type,
                changeType = QuestionChange.CREATED,
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun update(entity: Question): Question {
        val saved = super.update(entity)
        trackedEvents.publish { actor ->
            QuestionChanged(
                questionId = saved.id!!,
                surveyId = saved.surveyId,
                type = saved.type,
                changeType = QuestionChange.UPDATED,
                dirty = saved.dirty,
                dirtyFields = saved.dirtyFields,
                hasAnswers = saved.answers.isNotEmpty(),
                actor = actor
            )
        }
        return saved
    }

    @Transactional
    override fun delete(entity: Question) {
        val questionId = entity.id!!
        super.delete(entity)
        trackedEvents.publish { actor ->
            QuestionChanged(
                questionId = questionId,
                surveyId = entity.surveyId,
                type = entity.type,
                changeType = QuestionChange.DELETED,
                hasAnswers = entity.answers.isNotEmpty(),
                actor = actor
            )
        }
    }

    @Transactional
    override fun deleteById(id: Long) {
        val question = findById(id)
        delete(question)
    }

    /**
     * Get a lazy reference to a Question by ID.
     * Used when only the ID is needed for relationships (e.g., Answer → Question).
     * Does not trigger database fetch until the entity is actually accessed.
     */
    @Transactional(readOnly = true)
    fun getReferenceById(id: Long): Question {
        return repository.getReferenceById(id)
    }
}
