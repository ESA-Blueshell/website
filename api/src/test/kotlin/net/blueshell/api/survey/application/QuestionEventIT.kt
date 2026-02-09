package net.blueshell.api.survey.application

import net.blueshell.api.event.application.EventService
import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.testsupport.EventIntegrationTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class QuestionEventIT : EventIntegrationTestSupport() {

    @Autowired
    private lateinit var questions: QuestionService

    @Autowired
    private lateinit var answers: AnswerService

    @Autowired
    private lateinit var events: EventService

    @Autowired
    private lateinit var signUps: EventSignUpService

    @Autowired
    private lateinit var surveyFactory: SurveyFactory

    @Autowired
    private lateinit var questionFactory: QuestionFactory

    @Autowired
    private lateinit var answerFactory: AnswerFactory

    @Autowired
    private lateinit var eventFactory: EventFactory

    @Autowired
    private lateinit var committeeFactory: CommitteeFactory

    @Autowired
    private lateinit var userFactory: UserFactory

    @Test
    fun `creating non-description question clears signups for survey`() {
        val survey = persist(surveyFactory.createBasic())
        val committee = persist(committeeFactory.createBasic())
        val event = events.create(
            eventFactory.createWithCustomizations {
                it.committee = committee
                it.signUp = true
                it.signUpForm = survey
            }
        )

        val user = persist(userFactory.createBasic())
        val signUp = EventSignUp()
        signUp.event = event
        signUp.user = user
        signUps.create(signUp)

        assertTrue(signUps.findBySurveyId(survey.id!!).isNotEmpty())

        val question = questionFactory.createWithCustomizations {
            it.survey = survey
            it.type = QuestionType.OPEN
        }

        questions.create(question)

        assertEquals(0, signUps.findBySurveyId(survey.id!!).size)
    }

    @Test
    fun `deleting question with answers removes answers`() {
        val survey = persist(surveyFactory.createBasic())
        val question = questionFactory.createWithCustomizations {
            it.survey = survey
            it.type = QuestionType.OPEN
        }
        val savedQuestion = questions.create(question)

        val answer = answerFactory.createForQuestion(savedQuestion)
        val savedAnswer = answers.create(answer)

        assertTrue(answers.findByQuestionId(savedQuestion.id!!).any { it.id == savedAnswer.id })

        questions.delete(savedQuestion)

        assertEquals(0, answers.findByQuestionId(savedQuestion.id!!).size)
    }
}
