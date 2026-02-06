package net.blueshell.api.mapper.event

import net.blueshell.api.factory.dto.event.EventSignUpDTOFactory
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.dto.survey.QuestionDTOFactory
import net.blueshell.api.factory.model.EventSignUpFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.event.EventSignUp
import net.blueshell.api.model.survey.Answer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventSignUpMapperIT @Autowired constructor(
    private val eventSignUpMapper: EventSignUpMapper,
    private val eventSignUpDTOFactory: EventSignUpDTOFactory,
    private val questionDTOFactory: QuestionDTOFactory,
    private val answerDTOFactory: AnswerDTOFactory,
    private val eventSignUpFactory: EventSignUpFactory
) : MapperTestSupport() {
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
        val mappedDto = eventSignUpMapper.toDTO(reloaded)

        assertThat(reloaded.eventId).isEqualTo(event.id)
        assertThat(reloaded.userId).isEqualTo(user.id)
        assertThat(reloaded.answers).hasSize(1)
        assertThat(mappedDto.answers).hasSize(1)
    }
}
