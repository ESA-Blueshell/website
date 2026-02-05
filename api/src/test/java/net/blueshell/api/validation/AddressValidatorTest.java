package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.dto.AddressDTO;
import net.blueshell.api.factory.dto.AddressDTOFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for AddressDTO validation.
 */
@SpringBootTest
class AddressValidatorTest {

    private final Validator validator;
    private final AddressDTOFactory addressFactory;

    @Autowired
    AddressValidatorTest(Validator validator, AddressDTOFactory addressFactory) {
        this.validator = validator;
        this.addressFactory = addressFactory;
    }

    @Test
    void validAddressDTO_passesValidation() {
        AddressDTO dto = addressFactory.createBasic();
        Set<ConstraintViolation<AddressDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid AddressDTO should pass validation");
    }

    @Test
    void addressDTO_withInvalidCountryCode_failsValidation() {
        AddressDTO dto = addressFactory.createWithCustomizations(a -> a.setCountry("INVALID"));
        Set<ConstraintViolation<AddressDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("country")));
    }

    @Test
    void addressDTO_withEmptyCity_failsValidation() {
        AddressDTO dto = addressFactory.createWithCustomizations(a -> a.setCity(""));
        Set<ConstraintViolation<AddressDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("city")));
    }

    @Test
    void addressDTO_withEmptyStreet_failsValidation() {
        AddressDTO dto = addressFactory.createWithCustomizations(a -> a.setStreet(""));
        Set<ConstraintViolation<AddressDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("street")));
    }

    @Test
    void addressDTO_withEmptyHouseNumber_failsValidation() {
        AddressDTO dto = addressFactory.createWithCustomizations(a -> a.setHouseNumber(""));
        Set<ConstraintViolation<AddressDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("houseNumber")));
    }

    @Test
    void addressDTO_withEmptyZipCode_failsValidation() {
        AddressDTO dto = addressFactory.createWithCustomizations(a -> a.setZipCode(""));
        Set<ConstraintViolation<AddressDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("zipCode")));
    }
}
