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
    val mappedQuestions = questions!!.map { it.asEntity() }
    val questionsSet = survey.questions as MutableSet
    questionsSet.clear()
    questionsSet.addAll(mappedQuestions)
    survey.questions.forEach { it.survey = survey }
    return survey
}

fun QuestionDTO.asEntity(question: Question = Question()): Question {
    question.idx = idx!!
    // Note: survey reference must be set by caller using surveyRepository.getReferenceById(surveyId!!)
    question.type = type!!
    question.label = label!!
    question.choiceLabels = choiceLabels
    version?.let { question.version = it }
    return question
}

fun AnswerDTO.asEntity(answer: Answer = Answer()): Answer {
    // Note: question reference must be set by caller using questionRepository.getReferenceById(questionId!!)
    answer.optionSelections = optionSelections
    answer.textResponse = textResponse
    version?.let { answer.version = it }
    return answer
}
