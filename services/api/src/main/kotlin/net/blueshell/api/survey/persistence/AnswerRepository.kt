package net.blueshell.api.survey.persistence

import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findByQuestionSurvey_Id(surveyId: Long): MutableSet<Answer>
    fun findByQuestion_Id(questionId: Long): MutableSet<Answer>
}
