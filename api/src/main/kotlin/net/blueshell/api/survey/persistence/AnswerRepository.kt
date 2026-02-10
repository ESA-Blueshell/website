package net.blueshell.api.survey.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Suppress("FunctionName")
@Repository
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findBy_questionSurveyId(surveyId: Long): MutableSet<Answer>
    fun findBy_questionId(questionId: Long): MutableSet<Answer>
}
