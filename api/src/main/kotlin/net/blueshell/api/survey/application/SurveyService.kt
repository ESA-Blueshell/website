package net.blueshell.api.survey.application

import net.blueshell.api.survey.domain.model.Survey
import net.blueshell.api.survey.persistence.SurveyRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SurveyService @Autowired constructor(repository: SurveyRepository) :
    BaseModelService<Survey, Long, SurveyRepository>(repository)
