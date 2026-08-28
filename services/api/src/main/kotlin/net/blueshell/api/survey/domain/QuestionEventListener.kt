package net.blueshell.api.survey.domain

import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Cleans up orphan answers when a question is deleted. Question creation
 * and updates no longer cascade to sign-ups; that is opt-in via
 * UpdateEventRequest.removeExistingSignUps.
 */
@Component
class QuestionEventListener(
    private val answers: AnswerService,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: QuestionChanged) {
        if (evt.changeType == QuestionChange.DELETED && evt.hasAnswers) {
            answers.deleteAll(answers.findByQuestionId(evt.questionId))
        }
    }
}
