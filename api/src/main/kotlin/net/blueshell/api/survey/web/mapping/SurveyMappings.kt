package net.blueshell.api.survey.web.mapping

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.survey.web.dto.SurveyDTO

@Konverter
interface SurveyKonverter {
    fun toDTO(survey: Survey): SurveyDTO

    @Konvert(
        mappings = [
            Mapping(target = "questions", ignore = true),
            Mapping(target = "responseCount", ignore = true),
        ]
    )
    fun fromDTO(dto: SurveyDTO): Survey
}

@Konverter
interface QuestionKonverter {
    fun toDTO(question: Question): QuestionDTO

    fun fromDTO(dto: QuestionDTO): Question
}

@Konverter
interface AnswerKonverter {
    fun toDTO(answer: Answer): AnswerDTO

    fun fromDTO(dto: AnswerDTO): Answer
}

private val surveyKonverter = Konverter.get<SurveyKonverter>()
private val questionKonverter = Konverter.get<QuestionKonverter>()
private val answerKonverter = Konverter.get<AnswerKonverter>()

fun SurveyDTO.asEntity(): Survey {
    val survey = surveyKonverter.fromDTO(this)
    survey.questions.addAll(questions.map { it.asEntity() })
    return survey
}

fun QuestionDTO.asEntity(): Question = questionKonverter.fromDTO(this)

fun AnswerDTO.asEntity(): Answer = answerKonverter.fromDTO(this)

fun Survey.asDto(): SurveyDTO = surveyKonverter.toDTO(this)

fun Question.asDto(): QuestionDTO = questionKonverter.toDTO(this)

fun Answer.asDto(): AnswerDTO = answerKonverter.toDTO(this)
