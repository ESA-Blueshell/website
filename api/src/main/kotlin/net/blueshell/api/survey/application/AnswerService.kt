package net.blueshell.api.survey.application

import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.AnswerRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AnswerService @Autowired constructor(repository: AnswerRepository) :
    BaseModelService<Answer, Long, AnswerRepository>(repository) {
    fun findBySurveyId(surveyId: Long): MutableSet<Answer> {
        return repository.findBy_questionSurveyId(surveyId)
    }

    fun findByQuestionId(questionId: Long): MutableSet<Answer> {
        return repository.findBy_questionId(questionId)
    }
}
