package net.blueshell.api.feature.survey.service

import net.blueshell.api.feature.survey.model.Question
import net.blueshell.api.feature.survey.repository.QuestionRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class QuestionService @Autowired constructor(repository: QuestionRepository) :
    BaseModelService<Question, Long, QuestionRepository>(repository)
