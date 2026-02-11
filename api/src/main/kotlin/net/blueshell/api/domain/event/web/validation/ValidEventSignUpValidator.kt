package net.blueshell.api.domain.event.web.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.web.dto.EventSignUpDTO
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.domain.survey.persistence.Question
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.util.CollectionUtils
import java.util.*
import java.util.function.Supplier
import java.util.stream.Collectors

class ValidEventSignUpValidator @Autowired constructor(private val events: EventService) :
    ConstraintValidator<ValidEventSignUp, EventSignUpDTO> {
    override fun isValid(dto: EventSignUpDTO?, ctx: ConstraintValidatorContext): Boolean {
        if (dto == null) return true
        val eventId = dto.eventId!!

        ctx.disableDefaultConstraintViolation()

        val event = findEvent(eventId) ?: return violation(ctx, "eventId", "Unknown event.")

        val form = event.signUpForm // survey = sign-up form
        if (form == null || CollectionUtils.isEmpty(form.questions)) {
            return true // no questions -> nothing to validate
        }

        val answers = dto.answers

        // Collect all question IDs on the form (keep insertion order for stable error messages)
        val formQuestionIds: MutableSet<Long> = form.questions
            .stream()
            .filter { q: Question? -> q!!.type != QuestionType.DESCRIPTION }
            .map { obj: Question? -> obj!!.id }
            .filter { obj: Long? -> Objects.nonNull(obj) }
            .map { obj: Long? -> obj as Long }
            .collect(Collectors.toCollection(Supplier { LinkedHashSet() }))

        if (formQuestionIds.isEmpty()) {
            return true // defensively allow if form has no identifiable questions
        }

        var valid = true
        val provided: MutableSet<Long> = LinkedHashSet()

        for (i in answers!!.indices) {
            val a = answers[i]

            val qid = a.questionId!!

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
        val missing: MutableSet<Long> = LinkedHashSet(formQuestionIds)
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
