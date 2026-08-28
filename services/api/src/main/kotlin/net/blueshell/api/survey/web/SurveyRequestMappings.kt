package net.blueshell.api.survey.web

import net.blueshell.api.survey.api.QuestionData
import net.blueshell.api.survey.api.SurveyData

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
