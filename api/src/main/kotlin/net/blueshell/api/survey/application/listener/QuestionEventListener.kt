package net.blueshell.api.survey.application.listener

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.shared.event.jpa.PostPersistEvent
import net.blueshell.api.shared.event.jpa.PostRemoveEvent
import net.blueshell.api.shared.event.jpa.PostUpdateEvent
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.survey.application.AnswerService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class QuestionEventListener(
    private val answers: AnswerService,
    private val signUps: EventSignUpService
) {
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onPersist(evt: PostPersistEvent<Question>) {
        val q = evt.source

        log.info("On persist question {}", q.id)

        // If a new description is added, there is no need to clear the survey.
        // If a new question is added, then we do need to wipe the answers and signups.
        // This is because the surveys will need to be filled in again.
        if (q.type != QuestionType.DESCRIPTION) {
            signUps.deleteAll(signUps.findBySurveyId(q.surveyId))
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onUpdate(evt: PostUpdateEvent<Question>) {
        val q = evt.source

        // When a question is updated, the survey will need to be re-filled.
        // Therefore, all answers for the survey need to be wiped.
        log.info("Question update dirty fields: {}", q.dirtyFields)
        if (!q.answers.isEmpty() && q.dirty) {
            signUps.deleteAll(signUps.findBySurveyId(q.surveyId))
        }
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onDelete(evt: PostRemoveEvent<Question>) {
        val q = evt.source

        // If a question is just removed, without anything else being changed
        // There is no need to wipe all existing answers for the survey.
        // All existing answers only need to be wiped if a new question is added to a survey
        // Or if an existing question is modified and thus all questions need to be re-answered
        if (!q.answers.isEmpty()) {
            answers.deleteAll(q.answers as MutableSet<Answer>)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(QuestionEventListener::class.java)
    }
}
