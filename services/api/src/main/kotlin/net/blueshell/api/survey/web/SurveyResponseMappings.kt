package net.blueshell.api.survey.web

import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey

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
        required = this.required,
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
