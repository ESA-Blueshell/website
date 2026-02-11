package net.blueshell.api.factory.model.survey

import com.github.javafaker.Faker
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Survey model test instances.
 */
@Component
class SurveyFactory(
    private val faker: Faker,
    private val questionFactory: QuestionFactory
) {

    fun createBasic(): Survey {
        val survey = Survey()
        survey.responseCount = 0L
        return survey
    }

    fun createFull(): Survey {
        val survey = createBasic()
        repeat(faker.number().numberBetween(3, 8)) {
            survey.addQuestion(questionFactory.createForSurvey(survey))
        }
        return survey
    }

    fun createWithCustomizations(customizer: Consumer<Survey>): Survey {
        val survey = createFull()
        customizer.accept(survey)
        return survey
    }

    fun createWithQuestions(count: Int): Survey {
        return createWithCustomizations { survey ->
            val questions = mutableListOf<Question>()
            repeat(count) {
                questions.add(questionFactory.createFull())
            }
            survey.replaceQuestions(questions)
        }
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
