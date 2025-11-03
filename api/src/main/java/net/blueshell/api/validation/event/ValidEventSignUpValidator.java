package net.blueshell.api.validation.event;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.model.survey.Survey;
import net.blueshell.api.service.event.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ValidEventSignUpValidator implements ConstraintValidator<ValidEventSignUp, EventSignUpDTO> {

    @Autowired
    private EventService events;

    @Override
    public boolean isValid(EventSignUpDTO dto, ConstraintValidatorContext ctx) {
        if (dto == null) return true;

        final Event event = findEvent(dto.getEventId());
        if (event == null) {
            return violation(ctx, "eventId", "Unknown event.");
        }

        final Survey form = event.getSignUpForm();   // survey = sign-up form
        if (form == null || CollectionUtils.isEmpty(form.getQuestions())) {
            return true; // no questions -> nothing to validate here
        }

        final List<AnswerDTO> answers = dto.getAnswers();
        if (answers == null) {
            return violation(ctx, "answers", "Answers are required for this event’s sign-up form.");
        }

        ctx.disableDefaultConstraintViolation();
        boolean valid = true;

        final Set<Long> requiredQIds = form.getQuestions().stream()
                .map(Question::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final Set<Long> providedQIds = new HashSet<>();
        final Set<Long> seen = new HashSet<>();

        for (int i = 0; i < answers.size(); i++) {
            final AnswerDTO a = answers.get(i);

            if (a == null || a.getQuestionId() == null) {
                violationAtAnswer(ctx, i, "questionId is required.");
                valid = false;
                continue;
            }

            final Long qid = a.getQuestionId();

            if (!requiredQIds.contains(qid)) {
                violationAtAnswer(ctx, i,
                        "Question does not belong to this event’s sign-up form (id=" + qid + ").");
                valid = false;
            }

            if (!seen.add(qid)) {
                violationAtAnswer(ctx, i,
                        "Duplicate answer for questionId " + qid + ".");
                valid = false;
            }

            providedQIds.add(qid);
        }

        // Must answer all questions exactly once
        final Set<Long> missing = new HashSet<>(requiredQIds);
        missing.removeAll(providedQIds);
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
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
        return false;
    }

    private void violationAtAnswer(ConstraintValidatorContext ctx, int index, String message) {
        ctx.buildConstraintViolationWithTemplate(message)
                .addPropertyNode("answers")
                .inIterable().atIndex(index)
                .addPropertyNode("questionId")
                .addConstraintViolation();
    }
}
