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
import tech.mappie.api.ObjectMappie

object SurveyToSurveyDTOMapper : ObjectMappie<Survey, SurveyDTO>()

object QuestionToQuestionDTOMapper : ObjectMappie<Question, QuestionDTO>()

object AnswerToAnswerDTOMapper : ObjectMappie<Answer, AnswerDTO>()

object SurveyToSurveyResponseMapper : ObjectMappie<Survey, SurveyResponse>()

object QuestionToQuestionResponseMapper : ObjectMappie<Question, QuestionResponse>()

object AnswerToAnswerResponseMapper : ObjectMappie<Answer, AnswerResponse>()

fun Survey.asDto(): SurveyDTO = SurveyToSurveyDTOMapper.map(this)

fun Question.asDto(): QuestionDTO = QuestionToQuestionDTOMapper.map(this)

fun Answer.asDto(): AnswerDTO = AnswerToAnswerDTOMapper.map(this)

fun Survey.asResponse(): SurveyResponse = SurveyToSurveyResponseMapper.map(this)

fun Question.asResponse(): QuestionResponse = QuestionToQuestionResponseMapper.map(this)

fun Answer.asResponse(): AnswerResponse = AnswerToAnswerResponseMapper.map(this)
