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
            Question(
                idx = qData.idx,
                survey = survey,
                type = qData.type,
                label = qData.label,
                choiceLabels = qData.choiceLabels?.toMutableList(),
            )
        }
        survey.replaceQuestions(questions)
        return survey
    }
}
