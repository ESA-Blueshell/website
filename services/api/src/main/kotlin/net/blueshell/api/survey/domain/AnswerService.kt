package net.blueshell.api.survey.domain

import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.AnswerRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AnswerService
    @Autowired
    constructor(
        repository: AnswerRepository,
    ) : BaseModelService<Answer, Long, AnswerRepository>(repository) {
        fun findBySurveyId(surveyId: Long): MutableSet<Answer> = repository.findByQuestionSurvey_Id(surveyId)

        fun findByQuestionId(questionId: Long): MutableSet<Answer> = repository.findByQuestion_Id(questionId)
    }
