package net.blueshell.api.domain.event.application.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.command.EventSignUpCandidate
import net.blueshell.api.domain.survey.persistence.Question
import net.blueshell.api.shared.enums.QuestionType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.LinkedHashSet

@Component
class ValidEventSignUpCommandValidator @Autowired constructor(
    private val events: EventService
) : ConstraintValidator<ValidEventSignUpCommand, EventSignUpCandidate> {
    override fun isValid(candidate: EventSignUpCandidate?, ctx: ConstraintValidatorContext): Boolean {
        if (candidate == null) return true

        val data = candidate.data
        val eventId = data.eventId

        val event = try {
            events.findById(eventId)
        } catch (ignored: Exception) {
            return violation(ctx, "eventId", "Unknown event.")
        }

        val form = event.signUpForm
        val questions = form?.questions ?: emptySet()
        val formQuestionIds = questions
            .filter { q: Question -> q.type != QuestionType.DESCRIPTION }
            .mapNotNull { it.id }
            .toCollection(LinkedHashSet())

        if (formQuestionIds.isEmpty()) {
            return true
        }

        val answers = data.answers
        val provided = LinkedHashSet<Long>()
        var valid = true

        answers.forEachIndexed { index, answer ->
            val questionId = answer.questionId

            if (!formQuestionIds.contains(questionId)) {
                violationAtQuestionId(
                    ctx,
                    index,
                    "Question does not belong to this event's sign-up form (id=$questionId)."
                )
                valid = false
            }

            if (!provided.add(questionId)) {
                violationAtQuestionId(ctx, index, "Duplicate answer for questionId $questionId.")
                valid = false
            }
        }

        val missing = LinkedHashSet(formQuestionIds)
        missing.removeAll(provided)
        if (missing.isNotEmpty()) {
            ctx.buildConstraintViolationWithTemplate("Missing answers for questionIds: $missing")
                .addPropertyNode("answers")
                .addConstraintViolation()
            valid = false
        }

        return valid
    }

    private fun violation(ctx: ConstraintValidatorContext, property: String, message: String): Boolean {
        ctx.disableDefaultConstraintViolation()
        ctx.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(property)
            .addConstraintViolation()
        return false
    }

    private fun violationAtQuestionId(ctx: ConstraintValidatorContext, index: Int, message: String) {
        ctx.disableDefaultConstraintViolation()
        ctx.buildConstraintViolationWithTemplate(message)
            .addPropertyNode("answers")
            .inIterable().atIndex(index)
            .addPropertyNode("questionId")
            .addConstraintViolation()
    }
}
