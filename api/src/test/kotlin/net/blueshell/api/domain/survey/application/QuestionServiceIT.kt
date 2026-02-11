package net.blueshell.api.domain.survey.application

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.application.EventSignUpService
import net.blueshell.api.domain.event.persistence.EventSignUp
import net.blueshell.api.factory.model.UserFactory
import net.blueshell.api.factory.model.committee.CommitteeFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.factory.model.survey.QuestionFactory
import net.blueshell.api.factory.model.survey.SurveyFactory
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.testsupport.ServiceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class QuestionServiceIT : ServiceTestSupport() {

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

    @Nested
    inner class Create {

        @Test
        fun `creating non-description question clears signups for survey`() {
            val survey = persist(surveyFactory.createBasic())
            val committee = persist(committeeFactory.createBasic())
            val nonPersistedEvent = eventFactory.createWithCustomizations {
                it.committee = committee
                it.signUp = true
                it.signUpForm = survey
            }
            println("Non Persisted event survey.id: ${nonPersistedEvent.signUpForm?.id}")
            println("Non Persisted event surveyId: ${nonPersistedEvent.signUpFormId}")
            val event = events.create(
                nonPersistedEvent
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
    }

    @Nested
    inner class Delete {

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

    @Nested
    inner class Update {

        @Test
        fun `updating question label removes answers`() {
            val survey = persist(surveyFactory.createBasic())
            val question = questionFactory.createWithCustomizations {
                it.survey = survey
                it.type = QuestionType.OPEN
            }
            val savedQuestion = questions.create(question)

            val answer = answerFactory.createForQuestion(savedQuestion)
            val savedAnswer = answers.create(answer)

            assertTrue(answers.findByQuestionId(savedQuestion.id!!).any { it.id == savedAnswer.id })

            savedQuestion.label = "${savedQuestion.label} updated"
            questions.update(savedQuestion)

            assertEquals(0, answers.findByQuestionId(savedQuestion.id!!).size)
        }

        @Test
        fun `updating question type removes answers`() {
            val survey = persist(surveyFactory.createBasic())
            val question = questionFactory.createWithCustomizations {
                it.survey = survey
                it.type = QuestionType.OPEN
            }
            val savedQuestion = questions.create(question)

            val answer = answerFactory.createForQuestion(savedQuestion)
            val savedAnswer = answers.create(answer)

            assertTrue(answers.findByQuestionId(savedQuestion.id!!).any { it.id == savedAnswer.id })

            savedQuestion.type = QuestionType.RADIO
            savedQuestion.choiceLabels = mutableListOf("Option A", "Option B")
            questions.update(savedQuestion)

            assertEquals(0, answers.findByQuestionId(savedQuestion.id!!).size)
        }

        @Test
        fun `updating question choice labels removes answers`() {
            val survey = persist(surveyFactory.createBasic())
            val question = questionFactory.createWithCustomizations {
                it.survey = survey
                it.type = QuestionType.RADIO
                it.choiceLabels = mutableListOf("Option A", "Option B")
            }
            val savedQuestion = questions.create(question)

            val answer = answerFactory.createForQuestion(savedQuestion)
            val savedAnswer = answers.create(answer)

            assertTrue(answers.findByQuestionId(savedQuestion.id!!).any { it.id == savedAnswer.id })

            savedQuestion.choiceLabels = mutableListOf("Option A", "Option B", "Option C")
            questions.update(savedQuestion)

            assertEquals(0, answers.findByQuestionId(savedQuestion.id!!).size)
        }
    }
}
