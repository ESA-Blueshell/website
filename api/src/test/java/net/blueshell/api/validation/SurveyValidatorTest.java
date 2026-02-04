package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.dto.survey.AnswerDTO;
import net.blueshell.api.dto.survey.QuestionDTO;
import net.blueshell.api.dto.survey.SurveyDTO;
import net.blueshell.api.factory.dto.survey.AnswerDTOFactory;
import net.blueshell.api.factory.dto.survey.QuestionDTOFactory;
import net.blueshell.api.factory.dto.survey.SurveyDTOFactory;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.repository.survey.QuestionRepository;
import net.blueshell.api.testutil.ModelTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Unit tests for Survey, Question, and Answer DTO validations.
 */
@SpringBootTest
class SurveyValidatorTest {

    @Autowired private Validator validator;
    @Autowired private SurveyDTOFactory surveyFactory;
    @Autowired private QuestionDTOFactory questionFactory;
    @Autowired private AnswerDTOFactory answerFactory;
    @MockitoBean private QuestionRepository questionRepository;

    private static Question mkQuestion(QuestionType type) {
        Question q = new Question();
        ModelTestUtils.setId(q, 42L);
        q.setType(type);
        if (type == QuestionType.RADIO || type == QuestionType.CHECKBOX) {
            q.setChoiceLabels(List.of("A", "B", "C"));
        }
        return q;
    }

    @Test
    void validSurveyDTO_passesValidation() {
        SurveyDTO dto = surveyFactory.createBasic();
        Set<ConstraintViolation<SurveyDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid SurveyDTO should pass validation");
    }

    @Test
    void surveyDTO_withEmptyQuestions_failsValidation() {
        SurveyDTO dto = surveyFactory.createWithCustomizations(s -> s.setQuestions(List.of()));
        Set<ConstraintViolation<SurveyDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("questions")));
    }

    @Test
    void validQuestionDTO_passesValidation() {
        QuestionDTO dto = questionFactory.createBasic();
        Set<ConstraintViolation<QuestionDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid QuestionDTO should pass validation");
    }

    @Test
    void questionDTO_withoutLabel_failsValidation() {
        QuestionDTO dto = questionFactory.createWithCustomizations(q -> q.setLabel(""));
        Set<ConstraintViolation<QuestionDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("label")));
    }

    @Test
    void questionDTO_withLongLabel_failsValidation() {
        String longLabel = "A".repeat(2056);
        QuestionDTO dto = questionFactory.createWithCustomizations(q -> q.setLabel(longLabel));
        Set<ConstraintViolation<QuestionDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("label")));
    }

    @Test
    void openQuestion_withChoiceLabels_failsValidation() {
        QuestionDTO dto = questionFactory.createWithCustomizations(q -> {
            q.setType(QuestionType.OPEN);
            q.setChoiceLabels(List.of("Choice 1", "Choice 2"));
        });
        Set<ConstraintViolation<QuestionDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void checkboxQuestion_withoutChoiceLabels_failsValidation() {
        QuestionDTO dto = questionFactory.createWithCustomizations(q -> {
            q.setType(QuestionType.CHECKBOX);
            q.setChoiceLabels(List.of());
        });
        Set<ConstraintViolation<QuestionDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
    }

    @Test
    void validAnswerDTO_passesValidation() {
        AnswerDTO dto = answerFactory.createBasic();
        when(questionRepository.findById(dto.getQuestionId()))
                .thenReturn(Optional.of(mkQuestion(QuestionType.OPEN)));

        Set<ConstraintViolation<AnswerDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid AnswerDTO should pass validation");
    }

    @Test
    void answerDTO_withoutQuestionId_failsValidation() {
        AnswerDTO dto = answerFactory.createWithCustomizations(a -> a.setQuestionId(null));
        Set<ConstraintViolation<AnswerDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("questionId")));
    }
}
