package net.blueshell.api.survey.repository

import net.blueshell.api.survey.model.Question
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : BaseRepository<Question, Long>