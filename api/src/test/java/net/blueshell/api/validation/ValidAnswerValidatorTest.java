package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.repository.survey.QuestionRepository;
import net.blueshell.api.validation.survey.ValidAnswerValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValidAnswerValidator (per-question validation rules).
 */
@SpringBootTest
class ValidAnswerValidatorTest {

    private QuestionRepository questions;
    private ValidAnswerValidator validator;

    @BeforeEach
    void setup() {
        questions = mock(QuestionRepository.class);
        validator = new ValidAnswerValidator(questions);
    }

    @Test
    void invalid_when_question_not_found() {
        var dto = new AnswerDTO();
        dto.setQuestionId(123L);
        when(questions.findById(123L)).thenReturn(Optional.empty());

        assertFalse(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void open_valid_when_non_empty_text() {
        long qid = 1L;
        var q = mock(Question.class);
        when(q.getType()).thenReturn(QuestionType.OPEN);
        when(questions.findById(qid)).thenReturn(Optional.of(q));

        var dto = new AnswerDTO();
        dto.setQuestionId(qid);
        dto.setTextResponse("hello");

        assertTrue(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void open_invalid_when_empty_text() {
        long qid = 2L;
        var q = mock(Question.class);
        when(q.getType()).thenReturn(QuestionType.OPEN);
        when(questions.findById(qid)).thenReturn(Optional.of(q));

        var dto = new AnswerDTO();
        dto.setQuestionId(qid);
        dto.setTextResponse(""); // invalid empty text

        assertFalse(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void checkbox_valid_when_size_matches_choices() {
        long qid = 3L;
        var q = mock(Question.class);
        when(q.getType()).thenReturn(QuestionType.CHECKBOX);
        when(q.getChoiceLabels()).thenReturn(List.of("A", "B", "C"));
        when(questions.findById(qid)).thenReturn(Optional.of(q));

        var dto = new AnswerDTO();
        dto.setQuestionId(qid);
        dto.setOptionSelections(List.of(true, false, true));

        assertTrue(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void checkbox_invalid_when_size_mismatch() {
        long qid = 4L;
        var q = mock(Question.class);
        when(q.getType()).thenReturn(QuestionType.CHECKBOX);
        when(q.getChoiceLabels()).thenReturn(List.of("A", "B", "C"));
        when(questions.findById(qid)).thenReturn(Optional.of(q));

        var dto = new AnswerDTO();
        dto.setQuestionId(qid);
        dto.setOptionSelections(List.of(true, false)); // mismatch

        assertFalse(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void radio_valid_when_exactly_one_true_and_size_matches() {
        long qid = 5L;
        var q = mock(Question.class);
        when(q.getType()).thenReturn(QuestionType.RADIO);
        when(q.getChoiceLabels()).thenReturn(List.of("Red", "Blue"));
        when(questions.findById(qid)).thenReturn(Optional.of(q));

        var dto = new AnswerDTO();
        dto.setQuestionId(qid);
        dto.setOptionSelections(List.of(true, false));

        assertTrue(validator.isValid(dto, mock(ConstraintValidatorContext.class)));
    }

    @Test
    void radio_invalid_when_zero_or_multiple_selected() {
        long qid = 6L;
        var q = mock(Question.class);
        when(q.getType()).thenReturn(QuestionType.RADIO);
        when(q.getChoiceLabels()).thenReturn(List.of("Red", "Blue"));
        when(questions.findById(qid)).thenReturn(Optional.of(q));

        var ctx = mock(ConstraintValidatorContext.class);

        var none = new AnswerDTO();
        none.setQuestionId(qid);
        none.setOptionSelections(List.of(false, false));
        assertFalse(validator.isValid(none, ctx));

        var many = new AnswerDTO();
        many.setQuestionId(qid);
        many.setOptionSelections(List.of(true, true));
        assertFalse(validator.isValid(many, ctx));

        var mismatch = new AnswerDTO();
        mismatch.setQuestionId(qid);
        mismatch.setOptionSelections(List.of(true, false, false));
        assertFalse(validator.isValid(mismatch, ctx));
    }
}
