package net.blueshell.api.survey.web.dto

import io.mcarle.konvert.api.Konvert
import io.mcarle.konvert.api.Konverter
import io.mcarle.konvert.api.Mapping
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey

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
    val mappedQuestions = questions.map { it.asEntity() }
    survey.questions.addAll(mappedQuestions)
    version?.let { survey.version = it }
    return survey
}

fun QuestionDTO.asEntity(): Question = questionKonverter.fromDTO(this)

fun AnswerDTO.asEntity(answer: Answer = Answer()): Answer {
    val mapped = answerKonverter.fromDTO(this)
    answer.questionId = mapped.questionId
    answer.optionSelections = mapped.optionSelections?.toMutableList()
    answer.textResponse = mapped.textResponse
    answer.version = version
    return answer
}
