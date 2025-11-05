package net.blueshell.api.validation.event;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.model.survey.Survey;
import net.blueshell.api.service.event.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidEventSignUpValidator implements ConstraintValidator<ValidEventSignUp, EventSignUpDTO> {

    private final EventService events;

    @Autowired
    public ValidEventSignUpValidator(EventService events) {
        this.events = events;
    }

    @Override
    public boolean isValid(EventSignUpDTO dto, ConstraintValidatorContext ctx) {
        if (dto == null) return true;

        ctx.disableDefaultConstraintViolation();

        final Event event = findEvent(dto.getEventId());
        if (event == null) {
            return violation(ctx, "eventId", "Unknown event.");
        }

        final Survey form = event.getSignUpForm(); // survey = sign-up form
        if (form == null || CollectionUtils.isEmpty(form.getQuestions())) {
            return true; // no questions -> nothing to validate
        }

        final List<AnswerDTO> answers = dto.getAnswers();
        if (answers == null) {
            return violation(ctx, "answers", "Answers are required for this event’s sign-up form.");
        }

        // Collect all question IDs on the form (keep insertion order for stable error messages)
        Set<Long> formQuestionIds = form.getQuestions()
                .stream()
                .filter(q -> q.getType() != QuestionType.DESCRIPTION)
                .map(Question::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (formQuestionIds.isEmpty()) {
            return true; // defensively allow if form has no identifiable questions
        }

        boolean valid = true;
        final Set<Long> provided = new LinkedHashSet<>();

        for (int i = 0; i < answers.size(); i++) {
            final AnswerDTO a = answers.get(i);

            if (a == null) {
                violationAtAnswer(ctx, i);
                valid = false;
                continue;
            }

            final Long qid = a.getQuestionId();
            if (qid == null) {
                violationAtQuestionId(ctx, i, "questionId is required.");
                valid = false;
                continue;
            }

            if (!formQuestionIds.contains(qid)) {
                violationAtQuestionId(ctx, i,
                        "Question does not belong to this event’s sign-up form (id=" + qid + ").");
                valid = false;
                // keep going to collect other errors
            }

            if (!provided.add(qid)) {
                violationAtQuestionId(ctx, i,
                        "Duplicate answer for questionId " + qid + ".");
                valid = false;
            }
        }

        // Must answer all questions exactly once
        final Set<Long> missing = new LinkedHashSet<>(formQuestionIds);
        missing.removeAll(provided);
        if (!missing.isEmpty()) {
            ctx.buildConstraintViolationWithTemplate("Missing answers for questionIds: " + missing)
                    .addPropertyNode("answers")
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }

    private Event findEvent(Long eventId) {
        try {
            // Ensure signUpForm.questions are fetched (entity graph / join fetch) in your service
            return events.findById(eventId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean violation(ConstraintValidatorContext ctx, String property, String message) {
        ctx.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
        return false;
    }

    private void violationAtAnswer(ConstraintValidatorContext ctx, int index) {
        ctx.buildConstraintViolationWithTemplate("Answer must not be null.")
                .addPropertyNode("answers")
                .inIterable().atIndex(index)
                .addConstraintViolation();
    }

    private void violationAtQuestionId(ConstraintValidatorContext ctx, int index, String message) {
        ctx.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("answers")
                .inIterable().atIndex(index)
                .addPropertyNode("questionId")
                .addConstraintViolation();
    }
}
