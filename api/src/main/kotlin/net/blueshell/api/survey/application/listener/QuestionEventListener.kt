package net.blueshell.api.survey.application.listener

import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.application.AnswerService
import net.blueshell.api.survey.application.event.QuestionChangeType
import net.blueshell.api.survey.application.event.QuestionChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Component
class QuestionEventListener(
    private val answers: AnswerService,
    private val signUps: EventSignUpService
) {
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun onChange(evt: QuestionChangedEvent) {
        when (evt.changeType) {
            QuestionChangeType.CREATED -> {
                log.info("On persist question {}", evt.questionId)
                if (evt.type != QuestionType.DESCRIPTION) {
                    signUps.deleteAll(signUps.findBySurveyId(evt.surveyId))
                }
            }
            QuestionChangeType.UPDATED -> {
                log.info("Question update dirty fields: {}", evt.dirtyFields)
                if (evt.hasAnswers && evt.dirty) {
                    signUps.deleteAll(signUps.findBySurveyId(evt.surveyId))
                }
            }
            QuestionChangeType.DELETED -> {
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
