package net.blueshell.api.survey.persistence.repository

import net.blueshell.api.shared.repository.BaseRepository
import net.blueshell.api.survey.persistence.Question
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : BaseRepository<Question, Long>