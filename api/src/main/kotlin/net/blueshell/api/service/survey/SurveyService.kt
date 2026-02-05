package net.blueshell.api.service.survey

import net.blueshell.api.service.base.BaseModelService
import net.blueshell.api.model.survey.Survey
import net.blueshell.api.repository.survey.SurveyRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SurveyService @Autowired constructor(repository: SurveyRepository) :
    BaseModelService<Survey, Long, SurveyRepository>(repository)
