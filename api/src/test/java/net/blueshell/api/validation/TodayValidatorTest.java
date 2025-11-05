package net.blueshell.api.validation;

import jakarta.validation.ConstraintValidatorContext;
import net.blueshell.api.config.FactoryConfig;
import net.blueshell.api.config.ValidatorConfig;
import net.blueshell.api.validation.date.TodayValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@SpringBootTest
class TodayValidatorTest {

    private TodayValidator validator;
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        validator = new TodayValidator();
        context = mock(ConstraintValidatorContext.class);
    }

    @Test
    void todayIsValid() {
        assertTrue(validator.isValid(LocalDate.now(), context));
    }

    @Test
    void yesterdayIsInvalid() {
        assertFalse(validator.isValid(LocalDate.now().minusDays(1), context));
    }

    @Test
    void tomorrowIsInvalid() {
        assertFalse(validator.isValid(LocalDate.now().plusDays(1), context));
    }

    @Test
    void nullIsValid() {
        assertTrue(validator.isValid(null, context));
    }
}