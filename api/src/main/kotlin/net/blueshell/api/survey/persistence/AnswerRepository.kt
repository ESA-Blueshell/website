package net.blueshell.api.survey.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Suppress("FunctionName")
@Repository
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findByQuestionSurveyId(surveyId: Long): MutableSet<Answer>
    fun findByQuestionId(questionId: Long): MutableSet<Answer>
}
