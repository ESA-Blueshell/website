package net.blueshell.api.domain.survey.persistence.repository

import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.shared.repository.BaseRepository
import org.springframework.stereotype.Repository

@Repository
@Suppress("FunctionName")
interface AnswerRepository : BaseRepository<Answer, Long> {
    fun findByQuestionSurvey_Id(surveyId: Long): MutableSet<Answer>
    fun findByQuestion_Id(questionId: Long): MutableSet<Answer>
}
