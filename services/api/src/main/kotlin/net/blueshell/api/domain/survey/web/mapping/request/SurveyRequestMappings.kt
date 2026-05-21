package net.blueshell.api.domain.survey.web.mapping.request

import net.blueshell.api.domain.survey.command.QuestionData
import net.blueshell.api.domain.survey.command.SurveyData
import net.blueshell.api.domain.survey.web.dto.request.QuestionRequest
import net.blueshell.api.domain.survey.web.dto.request.SurveyRequest

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
        required = this.required ?: false,
    )
