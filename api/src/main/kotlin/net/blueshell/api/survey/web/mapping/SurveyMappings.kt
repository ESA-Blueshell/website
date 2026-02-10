package net.blueshell.api.survey.web.mapping

import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.web.dto.AnswerDTO
import net.blueshell.api.survey.web.dto.QuestionDTO
import net.blueshell.api.survey.web.dto.SurveyDTO
import tech.mappie.api.ObjectMappie

object SurveyToSurveyDTOMapper : ObjectMappie<Survey, SurveyDTO>()

object QuestionToQuestionDTOMapper : ObjectMappie<Question, QuestionDTO>()

object AnswerToAnswerDTOMapper : ObjectMappie<Answer, AnswerDTO>()

fun SurveyDTO.asEntity(survey: Survey = Survey()): Survey {
    survey.version = version!!
    survey.questions.addAll(questions!!.map { it.asEntity() })
    survey.questions.forEach { it.survey = survey }
    return survey
}

fun QuestionDTO.asEntity(question: Question = Question()): Question {
    question.idx = idx!!
    question.surveyId = surveyId!!
    question.type = type!!
    question.label = label!!
    question.choiceLabels = choiceLabels
    question.version = version!!
    return question
}

fun AnswerDTO.asEntity(answer: Answer = Answer()): Answer {
    answer.questionId = questionId!!
    answer.optionSelections = optionSelections
    answer.textResponse = textResponse
    answer.version = version!!
    return answer
}

fun Survey.asDto(): SurveyDTO = SurveyToSurveyDTOMapper.map(this)

fun Question.asDto(): QuestionDTO = QuestionToQuestionDTOMapper.map(this)

fun Answer.asDto(): AnswerDTO = AnswerToAnswerDTOMapper.map(this)
