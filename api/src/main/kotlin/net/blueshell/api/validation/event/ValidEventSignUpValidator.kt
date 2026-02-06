package net.blueshell.api.validation.event

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.common.enums.QuestionType
import net.blueshell.api.dto.event.EventSignUpDTO
import net.blueshell.api.model.event.Event
import net.blueshell.api.model.survey.Question
import net.blueshell.api.service.event.EventService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.CollectionUtils
import java.util.*
import java.util.function.Supplier
import java.util.stream.Collectors

class ValidEventSignUpValidator @Autowired constructor(private val events: EventService) :
    ConstraintValidator<ValidEventSignUp, EventSignUpDTO> {
    override fun isValid(dto: EventSignUpDTO?, ctx: ConstraintValidatorContext): Boolean {
        if (dto == null) return true
        val eventId = dto.eventId ?: return violation(ctx, "eventId", "eventId is required.")

        ctx.disableDefaultConstraintViolation()

        val event = findEvent(eventId) ?: return violation(ctx, "eventId", "Unknown event.")

        val form = event.signUpForm // survey = sign-up form
        if (form == null || CollectionUtils.isEmpty(form.questions)) {
            return true // no questions -> nothing to validate
        }

        val answers = dto.answers

        // Collect all question IDs on the form (keep insertion order for stable error messages)
        val formQuestionIds: MutableSet<Long?> = form.questions
            .stream()
            .filter { q: Question? -> q!!.type != QuestionType.DESCRIPTION }
            .map<Long?> { obj: Question? -> obj!!.id }
            .filter { obj: Long? -> Objects.nonNull(obj) }
            .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))

        if (formQuestionIds.isEmpty()) {
            return true // defensively allow if form has no identifiable questions
        }

        var valid = true
        val provided: MutableSet<Long?> = LinkedHashSet<Long?>()

        for (i in answers.indices) {
            val a = answers[i]

            val qid = a.questionId
            if (qid == null) {
                violationAtQuestionId(ctx, i, "questionId is required.")
                valid = false
                continue
            }

            if (!formQuestionIds.contains(qid)) {
                violationAtQuestionId(
                    ctx, i,
                    "Question does not belong to this event’s sign-up form (id=$qid)."
                )
                valid = false
                // keep going to collect other errors
            }

            if (!provided.add(qid)) {
                violationAtQuestionId(
                    ctx, i,
                    "Duplicate answer for questionId $qid."
                )
                valid = false
            }
        }

        // Must answer all questions exactly once
        val missing: MutableSet<Long?> = LinkedHashSet<Long?>(formQuestionIds)
        missing.removeAll(provided)
        if (!missing.isEmpty()) {
            ctx.buildConstraintViolationWithTemplate("Missing answers for questionIds: $missing")
                .addPropertyNode("answers")
                .addConstraintViolation()
            valid = false
        }

        return valid
    }

    private fun findEvent(eventId: Long): Event? {
        return try {
            // Ensure signUpForm.questions are fetched (entity graph / join fetch) in your service
            events.findById(eventId)
        } catch (ignored: Exception) {
            null
        }
    }

    private fun violation(ctx: ConstraintValidatorContext, property: String?, message: String?): Boolean {
        ctx.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(property)
            .addConstraintViolation()
        return false
    }

    private fun violationAtQuestionId(ctx: ConstraintValidatorContext, index: Int, message: String?) {
        ctx.buildConstraintViolationWithTemplate(message)
            .addPropertyNode("answers")
            .inIterable().atIndex(index)
            .addPropertyNode("questionId")
            .addConstraintViolation()
    }
}
