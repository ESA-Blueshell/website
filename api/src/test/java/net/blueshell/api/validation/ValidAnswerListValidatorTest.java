package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.service.survey.SurveyService;
import net.blueshell.api.validation.survey.ValidAnswerListValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ValidAnswerListValidator (duplicate question IDs, presence).
 */
@SpringBootTest
class ValidAnswerListValidatorTest {

    private ValidAnswerListValidator validator;
    private ConstraintValidatorContext context;
    private SurveyService surveyService;

    @BeforeEach
    void setUp() {
        surveyService = mock(SurveyService.class);
        validator = new ValidAnswerListValidator();
        // inject mocked service via reflection (validator is component in app)
        try {
            var field = ValidAnswerListValidator.class.getDeclaredField("surveys");
            field.setAccessible(true);
            field.set(validator, surveyService);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void validAnswerListWithUniqueQuestionIds() {
        AnswerDTO a1 = new AnswerDTO();
        a1.setQuestionId(1L);
        AnswerDTO a2 = new AnswerDTO();
        a2.setQuestionId(2L);

        List<AnswerDTO> answers = Arrays.asList(a1, a2);
        assertTrue(validator.isValid(answers, context));
    }

    @Test
    void answerListWithDuplicateQuestionIdsIsInvalid() {
        AnswerDTO a1 = new AnswerDTO();
        a1.setQuestionId(1L);
        AnswerDTO a2 = new AnswerDTO();
        a2.setQuestionId(1L);

        List<AnswerDTO> answers = Arrays.asList(a1, a2);

        ConstraintValidatorContext.ConstraintViolationBuilder builder =
                mock(ConstraintValidatorContext.ConstraintViolationBuilder.class);
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);

        assertFalse(validator.isValid(answers, context));
    }

    @Test
    void answerWithNullQuestionIdIsInvalid() {
        AnswerDTO a1 = new AnswerDTO();
        a1.setQuestionId(1L);
        AnswerDTO a2 = new AnswerDTO();
        a2.setQuestionId(null);

        List<AnswerDTO> answers = Arrays.asList(a1, a2);
        assertFalse(validator.isValid(answers, context));
    }

    @Test
    void nullAnswerListIsValid() {
        assertTrue(validator.isValid(null, context));
    }

    @Test
    void emptyAnswerListIsValid() {
        assertTrue(validator.isValid(Collections.emptyList(), context));
    }
}
