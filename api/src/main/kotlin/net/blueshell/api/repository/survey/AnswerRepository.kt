package net.blueshell.api.repository.survey

import net.blueshell.api.model.survey.Answer
import net.blueshell.api.repository.base.BaseRepository
import org.springframework.stereotype.Repository

@Suppress("FunctionName")
@Repository
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findBy_questionSurveyId(surveyId: Long): MutableSet<Answer>
}