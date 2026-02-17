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

fun SurveyRequest.asDomainData(): SurveyData =
    SurveyData(
        questions = this.questions!!.map { it.asDomainData() },
    )

fun QuestionRequest.asDomainData(): QuestionData =
    QuestionData(
        idx = this.idx!!,
        type = this.type!!,
        label = this.label!!,
        choiceLabels = this.choiceLabels,
    )

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
