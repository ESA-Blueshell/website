package net.blueshell.api.repository.survey

import net.blueshell.api.base.BaseRepository
import net.blueshell.api.model.survey.Answer
import org.springframework.stereotype.Repository

@Repository
interface AnswerRepository : BaseRepository<Answer?> {
    fun findByQuestionSurveyId(surveyId: Long?): MutableSet<Answer?>?
}