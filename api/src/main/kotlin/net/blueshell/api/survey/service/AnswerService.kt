package net.blueshell.api.survey.service

import net.blueshell.api.survey.model.Answer
import net.blueshell.api.survey.repository.AnswerRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AnswerService @Autowired constructor(repository: AnswerRepository) :
    BaseModelService<Answer, Long, AnswerRepository>(repository) {
    fun findBySurveyId(surveyId: Long): MutableSet<Answer> {
        return repository.findBy_questionSurveyId(surveyId)
    }
}
