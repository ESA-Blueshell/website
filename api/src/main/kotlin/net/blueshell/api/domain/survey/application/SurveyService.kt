package net.blueshell.api.domain.survey.application

import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.domain.survey.persistence.repository.SurveyRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SurveyService @Autowired constructor(repository: SurveyRepository) :
    BaseModelService<Survey, Long, SurveyRepository>(repository) {
    @Transactional
    override fun create(entity: Survey): Survey {
        mergeAssociations(entity)
        return super.create(entity)
    }

    @Transactional
    override fun update(entity: Survey): Survey {
        mergeAssociations(entity)
        return super.update(entity)
    }

    private fun mergeAssociations(survey: Survey) {
        // Set parent references for one-to-many relationship
        survey.questions.forEach { question ->
            question.survey = survey
        }
    }
}
