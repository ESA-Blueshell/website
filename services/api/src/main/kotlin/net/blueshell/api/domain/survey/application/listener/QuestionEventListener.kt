package net.blueshell.api.domain.survey.application.listener

import net.blueshell.api.domain.survey.application.AnswerService
import net.blueshell.api.domain.survey.application.event.QuestionChange
import net.blueshell.api.domain.survey.application.event.QuestionChanged
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Reacts to question lifecycle events.
 *
 * Adding or editing a question no longer destroys existing sign-ups; that
 * cascade is now opt-in via UpdateEventRequest.removeExistingSignUps.
 * Deleting a question still cleans up its orphan answers so they don't
 * dangle in the database.
 */
@Component
class QuestionEventListener(
    private val answers: AnswerService,
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: QuestionChanged) {
        when (evt.changeType) {
            QuestionChange.CREATED -> {
                log.info("Question {} created — keeping existing sign-ups", evt.questionId)
            }

            QuestionChange.UPDATED -> {
                log.info("Question {} updated (dirty fields: {}) — keeping existing sign-ups", evt.questionId, evt.dirtyFields)
            }

            QuestionChange.DELETED -> {
                if (evt.hasAnswers) {
                    answers.deleteAll(answers.findByQuestionId(evt.questionId))
                }
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(QuestionEventListener::class.java)
    }
}
