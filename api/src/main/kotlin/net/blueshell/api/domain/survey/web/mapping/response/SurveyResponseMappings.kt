package net.blueshell.api.domain.survey.web.mapping.response

import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.web.dto.AnswerDTO
import net.blueshell.api.domain.survey.web.dto.AnswerResponse
import net.blueshell.api.domain.survey.web.dto.QuestionDTO
import net.blueshell.api.domain.survey.web.dto.QuestionResponse
import net.blueshell.api.domain.survey.web.dto.SurveyDTO
import net.blueshell.api.domain.survey.web.dto.SurveyResponse

fun Survey.asDto(): SurveyDTO =
    SurveyDTO(
        id = this.id,
        questions = this.questions.map { it.asDto() }.toMutableList(),
        responseCount = this.responseCount,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Question.asDto(): QuestionDTO =
    QuestionDTO(
        id = this.id,
        idx = this.idx,
        surveyId = this.surveyId,
        type = this.type,
        label = this.label,
        choiceLabels = this.choiceLabels,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Answer.asDto(): AnswerDTO =
    AnswerDTO(
        id = this.id,
        questionId = this.questionId,
        optionSelections = this.optionSelections,
        textResponse = this.textResponse,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Survey.asResponse(): SurveyResponse =
    SurveyResponse(
        id = this.id!!,
        questions = this.questions.map { it.asResponse() }.toMutableList(),
        responseCount = this.responseCount,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Question.asResponse(): QuestionResponse =
    QuestionResponse(
        id = this.id!!,
        idx = this.idx,
        surveyId = this.surveyId,
        type = this.type,
        label = this.label,
        choiceLabels = this.choiceLabels,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )

fun Answer.asResponse(): AnswerResponse =
    AnswerResponse(
        id = this.id!!,
        questionId = this.questionId,
        optionSelections = this.optionSelections,
        textResponse = this.textResponse,
        version = this.version,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
    )
