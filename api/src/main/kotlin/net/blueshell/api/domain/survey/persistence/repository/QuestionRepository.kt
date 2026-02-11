package net.blueshell.api.domain.survey.persistence.repository

import net.blueshell.api.shared.repository.BaseRepository
import net.blueshell.api.domain.survey.persistence.Question
import org.springframework.stereotype.Repository

@Repository
interface QuestionRepository : BaseRepository<Question, Long>