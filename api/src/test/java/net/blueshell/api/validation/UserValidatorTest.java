package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.dto.user.SimpleUserDTO;
import net.blueshell.api.factory.dto.user.AdvancedUserDTOFactory;
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory;
import net.blueshell.api.validation.group.Creation;
import net.blueshell.api.validation.group.Update;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for user DTO validation groups.
 */
@SpringBootTest
class UserValidatorTest {

    @Autowired private Validator validator;
    @Autowired private SimpleUserDTOFactory simpleUserFactory;
    @Autowired private AdvancedUserDTOFactory advancedUserFactory;

    @Test
    void validSimpleUserDTO_passesValidation() {
        SimpleUserDTO dto = simpleUserFactory.createBasic();
        Set<ConstraintViolation<SimpleUserDTO>> violations = validator.validate(dto, Creation.class);
        assertTrue(violations.isEmpty(), "Valid SimpleUserDTO should pass validation");
    }

    @Test
    void simpleUserDTO_withoutPassword_failsCreationValidation() {
        SimpleUserDTO dto = simpleUserFactory.createWithCustomizations(u -> u.setPassword(null));
        Set<ConstraintViolation<SimpleUserDTO>> violations = validator.validate(dto, Creation.class);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void simpleUserDTO_withWeakPassword_failsValidation() {
        SimpleUserDTO dto = simpleUserFactory.createWithCustomizations(u -> u.setPassword("weak"));
        Set<ConstraintViolation<SimpleUserDTO>> violations = validator.validate(dto, Creation.class);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("password")));
    }

    @Test
    void simpleUserDTO_withInvalidEmail_failsValidation() {
        SimpleUserDTO dto = simpleUserFactory.createWithCustomizations(u -> u.setEmail("invalid-email"));
        Set<ConstraintViolation<SimpleUserDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("email")));
    }

    @Test
    void simpleUserDTO_withEmptyUsername_failsValidation() {
        SimpleUserDTO dto = simpleUserFactory.createWithCustomizations(u -> u.setUsername(""));
        Set<ConstraintViolation<SimpleUserDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("username")));
    }

    @Test
    void validAdvancedUserDTO_passesValidation() {
        AdvancedUserDTO dto = advancedUserFactory.createBasic();
        Set<ConstraintViolation<AdvancedUserDTO>> violations = validator.validate(dto, Update.class);
        assertTrue(violations.isEmpty(), "Valid AdvancedUserDTO should pass validation");
    }

    @Test
    void advancedUserDTO_withoutDateOfBirth_failsValidation() {
        AdvancedUserDTO dto = advancedUserFactory.createWithCustomizations(u -> u.setDateOfBirth(null));
        Set<ConstraintViolation<AdvancedUserDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("dateOfBirth")));
    }

    @Test
    void advancedUserDTO_withoutNationality_failsValidation() {
        AdvancedUserDTO dto = advancedUserFactory.createWithCustomizations(u -> u.setNationality(null));
        Set<ConstraintViolation<AdvancedUserDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("nationality")));
    }
}
