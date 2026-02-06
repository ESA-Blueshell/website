package net.blueshell.api.service.survey

import net.blueshell.api.model.survey.Question
import net.blueshell.api.repository.survey.QuestionRepository
import net.blueshell.api.service.base.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class QuestionService @Autowired constructor(repository: QuestionRepository) :
    BaseModelService<Question, Long, QuestionRepository>(repository)
