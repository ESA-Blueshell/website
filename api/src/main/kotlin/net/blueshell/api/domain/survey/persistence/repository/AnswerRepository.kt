package net.blueshell.api.domain.survey.persistence.repository

import net.blueshell.api.shared.repository.BaseRepository
import net.blueshell.api.domain.survey.persistence.Answer
import org.springframework.stereotype.Repository

@Suppress("FunctionName")
@Repository
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findByQuestionSurveyId(surveyId: Long): MutableSet<Answer>
    fun findByQuestionId(questionId: Long): MutableSet<Answer>
}
