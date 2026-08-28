package net.blueshell.api.survey.api

import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
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
                required = qData.required,
            )
        }
        survey.replaceQuestions(questions)
        return survey
    }
}
