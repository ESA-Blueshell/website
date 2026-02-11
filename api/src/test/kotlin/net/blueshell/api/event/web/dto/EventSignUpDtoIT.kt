package net.blueshell.api.event.web.dto

import net.blueshell.api.event.application.EventSignUpService
import net.blueshell.api.event.persistence.EventSignUp
import net.blueshell.api.event.web.mapping.asEntity
import net.blueshell.api.factory.dto.event.EventSignUpDTOFactory
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.factory.model.event.EventSignUpFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.survey.persistence.Answer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventSignUpDtoIT @Autowired constructor(
    private val eventSignUpDTOFactory: EventSignUpDTOFactory,
    private val questionDTOFactory: QuestionDTOFactory,
    private val answerDTOFactory: AnswerDTOFactory,
    private val eventSignUpFactory: EventSignUpFactory,
    private val eventSignUpService: EventSignUpService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
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
            }
            (signUp.answers as MutableSet<Answer>).clear()

            val mapped = dto.asEntity(signUp)
            mapped.event = event

            val saved = eventSignUpService.create(mapped)
            flushAndClear()

            val reloaded = reload(EventSignUp::class.java, saved.id!!)

            assertThat(reloaded.eventId).isEqualTo(event.id)
            assertThat(reloaded.userId).isEqualTo(user.id)
            assertThat(reloaded.answers).hasSize(1)
        }
    }
}
