package net.blueshell.api.validation.eventSignUp;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.EventSignUpDTO;
import net.blueshell.api.model.Event;
import net.blueshell.api.model.FormQuestion;
import net.blueshell.api.repository.EventRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigInteger;
import java.util.List;

public class ValidAnswersForEventValidator implements ConstraintValidator<ValidAnswersForEvent, EventSignUpDTO> {


    private final EventRepository eventRepository;

    @Autowired
    public ValidAnswersForEventValidator(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @Override
    public boolean isValid(EventSignUpDTO dto, ConstraintValidatorContext context) {
        if (dto.getEventId() == null || dto.getFormAnswers() == null) {
            return false;
        }

        Event event = eventRepository.findById(dto.getEventId()).orElse(null);
        if (event == null || event.getSignUpForm() == null) {
            return false;
        }

        List<FormQuestion> questions = event.getSignUpForm();
        List<Object> answers = dto.getFormAnswers();

        if (questions.size() != answers.size()) {
            return false;
        }

        for (int i = 0; i < questions.size(); i++) {
            FormQuestion question = questions.get(i);
            Object answer = answers.get(i);

            if (!isValidAnswerForQuestion(answer, question)) {
                return false;
            }
        }

        return true;
    }

    private boolean isValidAnswerForQuestion(Object answer, FormQuestion question) {
        switch (question.getType()) {
            case "open":
            case "description":
                return answer instanceof String;
            case "radio":
                return answer instanceof BigInteger;
            case "checkbox":
                if (!(answer instanceof List)) return false;
                for (Object item : (List<?>) answer) {
                    if (!(item instanceof BigInteger)) return false;
                }
                return true;
            default:
                return false;
        }
    }
}