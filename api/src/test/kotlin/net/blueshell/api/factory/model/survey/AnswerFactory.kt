package net.blueshell.api.factory.model.survey

import com.github.javafaker.Faker
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.model.survey.Answer
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

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
