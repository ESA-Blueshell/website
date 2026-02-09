package net.blueshell.api.survey.persistence

import net.blueshell.api.survey.domain.model.Question
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : BaseRepository<Question, Long>