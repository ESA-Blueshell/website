package net.blueshell.api.survey.web.mapper

import net.blueshell.api.survey.web.dto.SurveyDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Survey
import org.springframework.stereotype.Component

@Component
class SurveyMapper(
    private val questionMapper: QuestionMapper
) : BaseMapper<Survey, SurveyDTO>() {
    override fun fromDTO(dto: SurveyDTO): Survey {
        val survey = Survey()
        val mappedQuestions = dto.questions.map { questionMapper.fromDTO(it) }
        survey.questions.addAll(mappedQuestions)
        dto.version?.let { survey.version = it }
        return survey
    }

    override fun toDTO(survey: Survey): SurveyDTO {
        return SurveyDTO(
            questions = survey.questions.map { questionMapper.toDTO(it) }.toMutableList(),
            responseCount = survey.responseCount
        ).also { dto ->
            dto.id = survey.id
            dto.version = survey.version
        }
    }
}

fun Survey.asDTO(mapper: SurveyMapper): SurveyDTO = mapper.toDTO(this)

fun SurveyDTO.asEntity(mapper: SurveyMapper): Survey = mapper.fromDTO(this)
