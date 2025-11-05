package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.validation.survey.ValidQuestionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for ValidQuestionValidator.
 */
@SpringBootTest
class ValidQuestionValidatorTest {

    private ValidQuestionValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new ValidQuestionValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void openQuestionWithoutChoicesIsValid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.OPEN);
        dto.setChoiceLabels(null);
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void openQuestionWithEmptyChoicesIsValid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.OPEN);
        dto.setChoiceLabels(Collections.emptyList());
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void checkboxQuestionWithValidChoicesIsValid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.CHECKBOX);
        dto.setChoiceLabels(Arrays.asList("Option 1", "Option 2", "Option 3"));
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void checkboxQuestionWithoutChoicesIsInvalid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.CHECKBOX);
        dto.setChoiceLabels(null);
        assertFalse(validator.isValid(dto, context));
    }

    @Test
    void checkboxQuestionWithEmptyChoicesIsInvalid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.CHECKBOX);
        dto.setChoiceLabels(Collections.emptyList());
        assertFalse(validator.isValid(dto, context));
    }

    @Test
    void checkboxQuestionWithEmptyChoiceLabelIsInvalid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.CHECKBOX);
        dto.setChoiceLabels(Arrays.asList("Option 1", "", "Option 3"));
        assertFalse(validator.isValid(dto, context));
    }

    @Test
    void radioQuestionWithValidChoicesIsValid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(QuestionType.RADIO);
        dto.setChoiceLabels(Arrays.asList("Yes", "No"));
        assertTrue(validator.isValid(dto, context));
    }

    @Test
    void nullQuestionIsValid() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void nullQuestionTypeIsValid() {
        QuestionDTO dto = new QuestionDTO();
        dto.setType(null);
        assertTrue(validator.isValid(dto, context));
    }
}
