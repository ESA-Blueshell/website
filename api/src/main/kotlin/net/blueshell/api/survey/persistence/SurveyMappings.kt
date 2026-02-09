package net.blueshell.api.survey.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.survey.web.dto.AnswerKonverter
import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.survey.web.dto.QuestionKonverter
import net.blueshell.api.survey.web.dto.SurveyDTO
import net.blueshell.api.survey.web.dto.SurveyKonverter

private val surveyKonverter = Konverter.get<SurveyKonverter>()
private val questionKonverter = Konverter.get<QuestionKonverter>()
private val answerKonverter = Konverter.get<AnswerKonverter>()

fun Survey.asDto(): SurveyDTO {
    val dto = surveyKonverter.toDTO(this)
    dto.questions = questions.map { it.asDto() }.toMutableList()
    dto.responseCount = responseCount
    return dto
}

fun Question.asDto(): QuestionDTO = questionKonverter.toDTO(this)

fun Answer.asDto(): AnswerDTO = answerKonverter.toDTO(this)
