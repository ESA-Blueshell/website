package net.blueshell.api.domain.survey.application.factory

import net.blueshell.api.domain.survey.command.SurveyData
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import org.springframework.stereotype.Component

@Component
class SurveyFactory {
    fun createFromData(data: SurveyData): Survey {
        val survey = Survey()
        val questions = data.questions.map { qData ->
            Question().apply {
                idx = qData.idx
                type = qData.type
                label = qData.label
                choiceLabels = qData.choiceLabels?.toMutableList()
                this.survey = survey
            }
        }
        survey.replaceQuestions(questions)
        return survey
    }
}
