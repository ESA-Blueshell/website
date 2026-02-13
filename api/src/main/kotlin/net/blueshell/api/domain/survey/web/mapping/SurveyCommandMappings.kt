package net.blueshell.api.domain.survey.web.mapping

import net.blueshell.api.domain.survey.command.QuestionData
import net.blueshell.api.domain.survey.command.SurveyData
import net.blueshell.api.domain.survey.web.dto.QuestionRequest
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
