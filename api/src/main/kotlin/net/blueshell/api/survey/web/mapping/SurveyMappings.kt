package net.blueshell.api.survey.web.mapping

import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.survey.web.dto.SurveyDTO
import tech.mappie.api.ObjectMappie

object SurveyToSurveyDTOMapper : ObjectMappie<Survey, SurveyDTO>()

object SurveyDTOToSurveyMapper : ObjectMappie<SurveyDTO, Survey>() {
    override fun map(from: SurveyDTO) = mapping {
        Survey::questions fromValue mutableListOf()
        Survey::responseCount fromValue 0
    }
}

object QuestionToQuestionDTOMapper : ObjectMappie<Question, QuestionDTO>()

object QuestionDTOToQuestionMapper : ObjectMappie<QuestionDTO, Question>()

object AnswerToAnswerDTOMapper : ObjectMappie<Answer, AnswerDTO>()

object AnswerDTOToAnswerMapper : ObjectMappie<AnswerDTO, Answer>()

fun SurveyDTO.asEntity(): Survey {
    val survey = SurveyDTOToSurveyMapper.map(this)
    survey.questions.addAll(questions.map { it.asEntity() })
    return survey
}

fun QuestionDTO.asEntity(): Question = QuestionDTOToQuestionMapper.map(this)

fun AnswerDTO.asEntity(): Answer = AnswerDTOToAnswerMapper.map(this)

fun Survey.asDto(): SurveyDTO = SurveyToSurveyDTOMapper.map(this)

fun Question.asDto(): QuestionDTO = QuestionToQuestionDTOMapper.map(this)

fun Answer.asDto(): AnswerDTO = AnswerToAnswerDTOMapper.map(this)
