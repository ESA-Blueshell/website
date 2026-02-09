package net.blueshell.api.event.mapper

import net.blueshell.api.factory.dto.event.EventSignUpDTOFactory
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.factory.model.survey.AnswerFactory
import net.blueshell.api.factory.model.event.EventSignUpFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.event.mapper.EventSignUpMapper
import net.blueshell.api.event.model.Event
import net.blueshell.api.event.model.EventSignUp
import net.blueshell.api.survey.model.Answer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventSignUpMapperIT @Autowired constructor(
    private val eventSignUpMapper: EventSignUpMapper,
    private val eventSignUpDTOFactory: EventSignUpDTOFactory,
    private val questionDTOFactory: QuestionDTOFactory,
    private val answerDTOFactory: AnswerDTOFactory,
    private val answerFactory: AnswerFactory,
    private val eventSignUpFactory: EventSignUpFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted sign up`() {
            var event = persistEvent()
            event.signUpForm = persist(surveyFactory.createFull())
            event = persist(event)

            val user = persistUser()
            val signUp = persist(eventSignUpFactory.createForEventAndUser(event, user))
            assertThat(signUp.answers.size).isGreaterThan(0)

            val dto = eventSignUpMapper.toDTO(signUp)

            assertThat(dto.id).isEqualTo(signUp.id)
            assertThat(dto.eventId).isEqualTo(signUp.eventId)
            assertThat(dto.userId).isEqualTo(signUp.userId)
            assertThat(dto.answers.size).isEqualTo(signUp.answers.size)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped answers`() {
            val event = persistEvent()
            val user = persistUser()
            val question = persistQuestionWithSurvey(persistSurvey())
            val answerDto = answerDTOFactory.createForQuestion(questionDTOFactory.createOpen().apply {
                id = question.id
            })
            val dto = eventSignUpDTOFactory.createBasic().apply {
                eventId = event.id!!
                userId = user.id
                answers = mutableListOf(answerDto)
            }
            val signUp = eventSignUpFactory.createBasic().apply {
                this.event = event
                this.user = user
                this.userId = user.id
            }
            (signUp.answers as MutableSet<Answer>).clear()

            val mapped = eventSignUpMapper.fromDTO(dto, signUp)
            mapped.event = event

            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(EventSignUp::class.java, saved.id!!)

            assertThat(reloaded.eventId).isEqualTo(event.id)
            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.answers).hasSize(1)
        }
    }
}
