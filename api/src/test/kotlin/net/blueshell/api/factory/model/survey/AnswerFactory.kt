package net.blueshell.api.factory.model.survey

import com.github.javafaker.Faker
import net.blueshell.api.domain.survey.persistence.Answer
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.domain.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Answer model test instances.
 */
@Component
class AnswerFactory(
    private val faker: Faker,
    private val questionFactory: QuestionFactory
) {

    fun createBasic(): Answer {
        val answer = Answer()
        val question = questionFactory.createFull()

        if (question.type == QuestionType.CHECKBOX || question.type == QuestionType.RADIO) {
            val selections = mutableListOf<Boolean>()
            val choices = question.choiceLabels
            if (choices != null) {
                repeat(choices.size) {
                    selections.add(faker.bool().bool())
                }
            }
            answer.optionSelections = selections
            answer.textResponse = null
        } else {
            answer.optionSelections = null
            answer.textResponse = faker.lorem().sentence()
        }
        return answer
    }

    fun createFull(): Answer = createBasic()

    fun createWithCustomizations(customizer: Consumer<Answer>): Answer {
        val answer = createFull()
        customizer.accept(answer)
        return answer
    }

    fun createForQuestion(question: Question): Answer {
        val answer = Answer()
        answer.question = question

        when (question.type) {
            QuestionType.OPEN -> {
                answer.textResponse = faker.lorem().sentence()
                answer.optionSelections = null
            }

            QuestionType.RADIO -> {
                answer.textResponse = null
                val choices = question.choiceLabels.orEmpty()
                if (choices.isNotEmpty()) {
                    val selectedIndex = faker.number().numberBetween(0, choices.size)
                    answer.optionSelections = MutableList(choices.size) { index -> index == selectedIndex }
                } else {
                    answer.optionSelections = mutableListOf()
                }
            }

            QuestionType.CHECKBOX -> {
                answer.textResponse = null
                val choices = question.choiceLabels.orEmpty()
                answer.optionSelections = MutableList(choices.size) { faker.bool().bool() }
            }

            QuestionType.DESCRIPTION -> {
                answer.textResponse = null
                answer.optionSelections = null
            }
        }

        return answer
    }

    fun createForSurvey(survey: Survey): List<Answer> {
        return survey.questions.map { question -> createForQuestion(question) }
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
