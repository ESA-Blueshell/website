package net.blueshell.api.domain.survey.web.mapping.request

import net.blueshell.api.domain.survey.command.QuestionData
import net.blueshell.api.domain.survey.command.SurveyData
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import net.blueshell.api.domain.survey.web.dto.QuestionRequest
import net.blueshell.api.domain.survey.web.dto.SurveyDTO
import net.blueshell.api.domain.survey.web.dto.SurveyRequest
import tech.mappie.api.ObjectMappie

object SurveyRequestToDataMapper : ObjectMappie<SurveyRequest, SurveyData>() {
    override fun map(from: SurveyRequest) = mapping {
        SurveyData::questions fromValue from.questions!!.map { QuestionRequestToDataMapper.map(it) }
    }
}

fun SurveyRequest.asDomainData(): SurveyData =
    SurveyRequestToDataMapper.map(this)

object QuestionRequestToDataMapper : ObjectMappie<QuestionRequest, QuestionData>() {
    override fun map(from: QuestionRequest) = mapping {
        QuestionData::idx fromValue from.idx!!
        QuestionData::type fromValue from.type!!
        QuestionData::label fromValue from.label!!
        QuestionData::choiceLabels fromValue from.choiceLabels
    }
}

fun QuestionRequest.asDomainData(): QuestionData =
    QuestionRequestToDataMapper.map(this)

fun SurveyDTO.asEntity(survey: Survey = Survey()): Survey {
    version?.let { survey.version = it }
    val mappedQuestions = questions!!.map { it.asEntity(survey = survey) }
    val questionsSet = survey.questions as MutableSet
    questionsSet.clear()
    questionsSet.addAll(mappedQuestions)
    return survey
}

fun QuestionDTO.asEntity(survey: Survey, question: Question? = null): Question {
    val mappedQuestion = question ?: Question(
        idx = idx!!,
        survey = survey,
        type = type!!,
        label = label!!,
        choiceLabels = choiceLabels,
    )
    mappedQuestion.idx = idx!!
    mappedQuestion.survey = survey
    mappedQuestion.type = type!!
    mappedQuestion.label = label!!
    mappedQuestion.choiceLabels = choiceLabels
    version?.let { mappedQuestion.version = it }
    return mappedQuestion
}

fun AnswerDTO.asEntity(question: Question, answer: Answer? = null): Answer {
    val mappedAnswer = answer ?: Answer(
        question = question,
        optionSelections = optionSelections,
        textResponse = textResponse,
    )
    mappedAnswer.question = question
    mappedAnswer.optionSelections = optionSelections
    mappedAnswer.textResponse = textResponse
    version?.let { mappedAnswer.version = it }
    return mappedAnswer
}
