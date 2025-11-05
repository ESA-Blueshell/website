package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.validation.survey.ValidQuestionListValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for ValidQuestionListValidator (unique, non-null indices).
 */
@SpringBootTest
class ValidQuestionListValidatorTest {

    private ValidQuestionListValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ValidQuestionListValidator();
        context = mock(ConstraintValidatorContext.class, RETURNS_DEEP_STUBS);
    }

    @Test
    void validQuestionListWithUniqueIndices() {
        QuestionDTO q1 = new QuestionDTO();
        q1.setIdx(1L);

        QuestionDTO q2 = new QuestionDTO();
        q2.setIdx(2L);

        List<QuestionDTO> questions = Arrays.asList(q1, q2);
        assertTrue(validator.isValid(questions, context));
    }

    @Test
    void questionListWithDuplicateIndicesIsInvalid() {
        QuestionDTO q1 = new QuestionDTO();
        q1.setIdx(1L);

        QuestionDTO q2 = new QuestionDTO();
        q2.setIdx(1L);

        List<QuestionDTO> questions = Arrays.asList(q1, q2);
        assertFalse(validator.isValid(questions, context));
    }

    @Test
    void questionWithNullIndexIsInvalid() {
        QuestionDTO q1 = new QuestionDTO();
        q1.setIdx(1L);

        QuestionDTO q2 = new QuestionDTO();
        q2.setIdx(null);

        List<QuestionDTO> questions = Arrays.asList(q1, q2);
        assertFalse(validator.isValid(questions, context));
    }

    @Test
    void nullQuestionListIsValid() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void emptyQuestionListIsValid() {
        assertTrue(validator.isValid(Collections.emptyList(), context));
    }
}
