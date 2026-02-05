package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.event.EventSignUpDTO;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.model.survey.Survey;
import net.blueshell.api.repository.survey.QuestionRepository;
import net.blueshell.api.service.event.EventService;
import net.blueshell.api.testutil.ModelTestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for composite EventSignUpDTO validator using mocked survey metadata.
 */
@SpringBootTest
class ValidEventSignUpValidatorTest {

    private final Validator validator;

    @MockitoBean private QuestionRepository questionRepository;
    @MockitoBean private EventService eventService;

    private static final long Q_OPEN = 101L;
    private static final long Q_RADIO = 102L;
    private static final long Q_CHECK = 103L;

    @Autowired
    ValidEventSignUpValidatorTest(Validator validator) {
        this.validator = validator;
    }

    @BeforeEach
    void setupQuestions() {
        var openQ = mkQuestion(Q_OPEN, QuestionType.OPEN, null);
        var radioQ = mkQuestion(Q_RADIO, QuestionType.RADIO, List.of("A", "B", "C"));
        var checkboxQ = mkQuestion(Q_CHECK, QuestionType.CHECKBOX, List.of("X", "Y", "Z", "W"));

        when(questionRepository.findById(Q_OPEN)).thenReturn(Optional.of(openQ));
        when(questionRepository.findById(Q_RADIO)).thenReturn(Optional.of(radioQ));
        when(questionRepository.findById(Q_CHECK)).thenReturn(Optional.of(checkboxQ));

        var form = new Survey();
        form.getQuestions().addAll(Set.of(openQ, radioQ, checkboxQ));

        var event = new Event();
        ModelTestUtils.setId(event, 1L);
        event.setSignUpForm(form);

        when(eventService.findById(1L)).thenReturn(event);
    }

    @Test
    void valid_payload_with_all_question_types_passes_validation() {
        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, "I love this event"));
        answers.add(radioAnswer(List.of(true, false, false)));
        answers.add(checkboxAnswer(List.of(true, false, true, false)));

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Expected a valid EventSignUpDTO");
    }

    @Test
    void radio_with_zero_selected_is_invalid() {
        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, "ok"));
        answers.add(radioAnswer(List.of(false, false, false)));
        answers.add(checkboxAnswer(List.of(true, false, false, false)));

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("answers")));
    }

    @Test
    void radio_with_multiple_selected_is_invalid() {
        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, "ok"));
        answers.add(radioAnswer(List.of(true, true, false)));
        answers.add(checkboxAnswer(List.of(true, false, true, false)));

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("answers")));
    }

    @Test
    void checkbox_with_length_mismatch_is_invalid() {
        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, "ok"));
        answers.add(radioAnswer(List.of(false, true, false)));
        answers.add(checkboxAnswer(List.of(true, false, true)));

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("answers")));
    }

    @Test
    void open_without_text_is_invalid() {
        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, ""));
        answers.add(radioAnswer(List.of(false, true, false)));
        answers.add(checkboxAnswer(List.of(true, false, true, false)));

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("answers")));
    }

    @Test
    void duplicate_answers_for_same_question_are_invalid() {
        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, "text"));
        answers.add(radioAnswer(List.of(true, false, false)));
        answers.add(checkboxAnswer(List.of(true, false, false, false)));
        answers.add(radioAnswer(List.of(false, true, false))); // duplicate

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("answers")));
    }

    @Test
    void answer_referring_to_unknown_question_is_invalid() {
        long unknownId = 9999L;
        when(questionRepository.findById(unknownId)).thenReturn(Optional.empty());

        List<AnswerDTO> answers = new ArrayList<>();
        answers.add(openAnswer(Q_OPEN, "text"));
        answers.add(radioAnswer(List.of(true, false, false)));
        answers.add(checkboxAnswer(List.of(true, false, false, false)));
        answers.add(openAnswer(unknownId, "should fail"));

        EventSignUpDTO dto = dtoWithAnswers(answers);
        Set<ConstraintViolation<EventSignUpDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("answers")));
    }

    // helpers

    private static Question mkQuestion(long id, QuestionType type, List<String> choices) {
        Question q = new Question();
        ModelTestUtils.setId(q, id);
        q.setType(type);
        q.setChoiceLabels(choices);
        return q;
    }

    private static EventSignUpDTO dtoWithAnswers(List<AnswerDTO> answers) {
        EventSignUpDTO dto = new EventSignUpDTO();
        dto.setEventId(1L);
        dto.setUserId(42L);
        dto.setAnswers(answers);
        return dto;
    }

    private static AnswerDTO openAnswer(long questionId, String text) {
        AnswerDTO a = new AnswerDTO();
        a.setQuestionId(questionId);
        a.setTextResponse(text);
        a.setOptionSelections(null);
        return a;
    }

    private static AnswerDTO radioAnswer(List<Boolean> selections) {
        AnswerDTO a = new AnswerDTO();
        a.setQuestionId(ValidEventSignUpValidatorTest.Q_RADIO);
        a.setTextResponse(null);
        a.setOptionSelections(selections);
        return a;
    }

    private static AnswerDTO checkboxAnswer(List<Boolean> selections) {
        AnswerDTO a = new AnswerDTO();
        a.setQuestionId(ValidEventSignUpValidatorTest.Q_CHECK);
        a.setTextResponse(null);
        a.setOptionSelections(selections);
        return a;
    }
}
