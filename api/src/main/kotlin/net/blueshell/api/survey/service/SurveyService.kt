package net.blueshell.api.survey.service

import net.blueshell.api.survey.model.Survey
import net.blueshell.api.survey.repository.SurveyRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class SurveyService @Autowired constructor(repository: SurveyRepository) :
    BaseModelService<Survey, Long, SurveyRepository>(repository)
