package net.blueshell.api.repository.survey

import net.blueshell.api.model.survey.Question
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : BaseRepository<Question, Long>