package net.blueshell.api.factory.dto.event

import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.event.api.dto.EventSignUpDTO
import net.blueshell.api.survey.api.dto.AnswerDTO
import net.blueshell.api.survey.api.dto.SurveyDTO
import net.blueshell.api.user.api.dto.SimpleUserDTO
import net.blueshell.api.factory.dto.BaseDtoFactory
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import org.springframework.stereotype.Component

/**
 * Factory for EventSignUpDTO test instances.
 */
@Component
class EventSignUpDTOFactory(
    private val answerFactory: AnswerDTOFactory,
    private val userFactory: SimpleUserDTOFactory,
    private val surveyFactory: SurveyDTOFactory
) : BaseDtoFactory<EventSignUpDTO>() {

    override fun targetType(): Class<EventSignUpDTO> = EventSignUpDTO::class.java

    override fun createBasic(): EventSignUpDTO {
        val dto = EventSignUpDTO()
        dto.eventId = nextId()
        val user: SimpleUserDTO = userFactory.createBasic()
        dto.user = user
        dto.userId = user.id
        dto.guest = null

        val survey: SurveyDTO = surveyFactory.createBasic()
        val answers: List<AnswerDTO> = survey.questions.map { answerFactory.createForQuestion(it) }
        dto.answers = answers.toMutableList()

        return dto
    }

    fun createForSurvey(survey: SurveyDTO): EventSignUpDTO {
        val dto = EventSignUpDTO()
        dto.eventId = nextId()
        val user: SimpleUserDTO = userFactory.createBasic()
        dto.user = user
        dto.userId = user.id
        dto.guest = null

        val answers: List<AnswerDTO> = survey.questions.map { answerFactory.createForQuestion(it) }
        dto.answers = answers.toMutableList()

        return dto
    }

    fun createWithQuestionTypes(vararg questionTypes: QuestionType): EventSignUpDTO {
        val survey = surveyFactory.createWithQuestionTypes(*questionTypes)
        return createForSurvey(survey)
    }
}
