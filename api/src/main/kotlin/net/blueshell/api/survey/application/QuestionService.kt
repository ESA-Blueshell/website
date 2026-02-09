package net.blueshell.api.survey.application

import net.blueshell.api.shared.event.AfterCommitEventPublisher
import net.blueshell.api.survey.application.event.QuestionChangeType
import net.blueshell.api.survey.application.event.QuestionChangedEvent
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.QuestionRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class QuestionService @Autowired constructor(
    repository: QuestionRepository,
    private val events: AfterCommitEventPublisher
) : BaseModelService<Question, Long, QuestionRepository>(repository) {
    @Transactional
    override fun create(entity: Question): Question {
        val saved = super.create(entity)
        events.publish(
            QuestionChangedEvent(
                questionId = saved.id!!,
                surveyId = saved.surveyId,
                type = saved.type,
                changeType = QuestionChangeType.CREATED
            )
        )
        return saved
    }

    @Transactional
    override fun update(entity: Question): Question {
        val saved = super.update(entity)
        events.publish(
            QuestionChangedEvent(
                questionId = saved.id!!,
                surveyId = saved.surveyId,
                type = saved.type,
                changeType = QuestionChangeType.UPDATED,
                dirty = saved.dirty,
                dirtyFields = saved.dirtyFields,
                hasAnswers = saved.answers.isNotEmpty()
            )
        )
        return saved
    }

    @Transactional
    override fun delete(entity: Question) {
        val questionId = entity.id!!
        val event = QuestionChangedEvent(
            questionId = questionId,
            surveyId = entity.surveyId,
            type = entity.type,
            changeType = QuestionChangeType.DELETED,
            hasAnswers = entity.answers.isNotEmpty()
        )
        super.delete(entity)
        events.publish(event)
    }

    @Transactional
    override fun deleteById(id: Long) {
        val question = findById(id)
        delete(question)
    }
}
