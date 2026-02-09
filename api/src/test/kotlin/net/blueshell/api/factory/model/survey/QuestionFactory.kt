package net.blueshell.api.factory.model.survey

import com.github.javafaker.Faker
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.survey.model.Question
import net.blueshell.api.survey.model.Survey
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Question model test instances.
 */
@Component
class QuestionFactory(
    private val faker: Faker
) {

    fun createBasic(): Question {
        val question = Question()
        question.idx = faker.number().numberBetween(1, 100).toLong()
        question.type = faker.options().option(QuestionType::class.java)
        question.label = faker.lorem().sentence() + "?"
        question.answerCount = 0L
        return question
    }

    fun createFull(): Question {
        val question = createBasic()
        if (question.type == QuestionType.CHECKBOX || question.type == QuestionType.RADIO) {
            question.choiceLabels = generateChoiceLabels().toMutableList()
        }
        return question
    }

    fun createWithCustomizations(customizer: Consumer<Question>): Question {
        val question = createFull()
        customizer.accept(question)
        return question
    }

    fun createForSurvey(survey: Survey): Question {
        return createWithCustomizations { question ->
            question.survey = survey
        }
    }

    fun createMultipleChoice(): Question {
        return createWithCustomizations { question ->
            question.type = QuestionType.RADIO
            question.choiceLabels = mutableListOf("Option A", "Option B", "Option C", "Option D")
        }
    }

    fun createText(): Question {
        return createWithCustomizations { question ->
            question.type = QuestionType.OPEN
            question.choiceLabels = null
        }
    }

    private fun generateChoiceLabels(): List<String> {
        val count = faker.number().numberBetween(2, 6)
        val labels = listOf("Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree")
        return labels.subList(0, count)
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
