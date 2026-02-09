package net.blueshell.api.survey.application

import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.survey.persistence.SurveyRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SurveyService @Autowired constructor(repository: SurveyRepository) :
    BaseModelService<Survey, Long, SurveyRepository>(repository) {
    @Transactional
    override fun create(entity: Survey): Survey {
        mergeQuestions(entity)
        return super.create(entity)
    }

    @Transactional
    override fun update(entity: Survey): Survey {
        mergeQuestions(entity)
        return super.update(entity)
    }

    private fun mergeQuestions(survey: Survey) {
        survey.questions.forEach { question -> question.survey = survey }
    }
}
