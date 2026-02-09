package net.blueshell.api.survey.repository

import net.blueshell.api.survey.model.Answer
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Suppress("FunctionName")
@Repository
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findBy_questionSurveyId(surveyId: Long): MutableSet<Answer>
}