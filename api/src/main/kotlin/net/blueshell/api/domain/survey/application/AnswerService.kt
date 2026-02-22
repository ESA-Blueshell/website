package net.blueshell.api.domain.survey.application

import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.repository.AnswerRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AnswerService @Autowired constructor(repository: AnswerRepository) :
    BaseModelService<Answer, Long, AnswerRepository>(repository) {
    fun findBySurveyId(surveyId: Long): MutableSet<Answer> {
        return repository.findByQuestionSurvey_Id(surveyId)
    }

    fun findByQuestionId(questionId: Long): MutableSet<Answer> {
        return repository.findByQuestion_Id(questionId)
    }
}
