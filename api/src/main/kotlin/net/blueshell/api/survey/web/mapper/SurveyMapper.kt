package net.blueshell.api.survey.web.mapper

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.survey.web.dto.SurveyDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.survey.persistence.Survey
import org.springframework.stereotype.Component

@Konverter
interface SurveyKonverter {
    @Konvert(mappings = [Mapping(target = "questions", ignore = true)])
    fun toDTO(survey: Survey): SurveyDTO

    @Konvert(
        mappings = [
            Mapping(target = "questions", ignore = true),
            Mapping(target = "responseCount", ignore = true),
        ]
    )
    fun fromDTO(dto: SurveyDTO): Survey
}

@Component
class SurveyMapper(
    private val questionMapper: QuestionMapper
) : BaseMapper<Survey, SurveyDTO>() {
    private val konverter = konverter<SurveyKonverter>()

    override fun fromDTO(dto: SurveyDTO): Survey {
        val survey = konverter.fromDTO(dto)
        val mappedQuestions = dto.questions.map { questionMapper.fromDTO(it) }
        survey.questions.addAll(mappedQuestions)
        dto.version?.let { survey.version = it }
        return survey
    }

    override fun toDTO(survey: Survey): SurveyDTO {
        val dto = konverter.toDTO(survey)
        dto.questions = survey.questions.map { questionMapper.toDTO(it) }.toMutableList()
        dto.responseCount = survey.responseCount
        return dto
    }
}

fun Survey.asDTO(mapper: SurveyMapper): SurveyDTO = mapper.toDTO(this)

fun SurveyDTO.asEntity(mapper: SurveyMapper): Survey = mapper.fromDTO(this)
