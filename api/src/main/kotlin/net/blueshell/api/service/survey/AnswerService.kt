package net.blueshell.api.service.survey

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.model.survey.Answer
import net.blueshell.api.repository.survey.AnswerRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AnswerService @Autowired constructor(repository: AnswerRepository) :
    BaseModelService<Answer, AnswerRepository>(repository) {
    fun findBySurveyId(surveyId: Long): MutableSet<Answer> {
        return repository!!.findByQuestionSurveyId(surveyId)
    }
}
