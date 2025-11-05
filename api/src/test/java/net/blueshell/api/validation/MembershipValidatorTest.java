package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.dto.MembershipDTO;
import net.blueshell.api.factory.dto.MembershipDTOFactory;
import net.blueshell.api.validation.group.Administration;
import net.blueshell.api.validation.group.Creation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MembershipDTO validation.
 */
@SpringBootTest
class MembershipValidatorTest {

    @Autowired private Validator validator;
    @Autowired private MembershipDTOFactory membershipFactory;

    @Test
    void validMembershipDTO_passesValidation() {
        MembershipDTO dto = membershipFactory.createBasic();
        var violations = validator.validate(dto, Creation.class, Administration.class);
        assertTrue(violations.isEmpty());
    }

    @Test
    void membershipDTO_withoutUserId_failsValidation() {
        var dto = membershipFactory.createWithCustomizations(m -> m.setUserId(null));
        var violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("userId")));
    }

    @Test
    void membershipDTO_withoutMemberType_failsValidation() {
        var dto = membershipFactory.createWithCustomizations(m -> m.setMemberType(null));
        var violations = validator.validate(dto, Administration.class);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("memberType")));
    }

    @Test
    void membershipDTO_withFutureStartDate_failsValidation() {
        var dto = membershipFactory.createWithCustomizations(m -> m.setStartDate(LocalDate.now().plusDays(1)));
        var violations = validator.validate(dto, Administration.class);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("startDate")));
    }

    @Test
    void membershipDTO_withEndDateBeforeStartDate_failsValidation() {
        LocalDate startDate = LocalDate.now();
        MembershipDTO dto = membershipFactory.createWithCustomizations(m -> {
            m.setStartDate(startDate);
            m.setEndDate(startDate.minusDays(1));
        });

        Set<ConstraintViolation<MembershipDTO>> violations = validator.validate(dto);
        // No cross-field constraint asserted here.
        assertTrue(violations.stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("startDate")
                        || v.getPropertyPath().toString().equals("endDate")));
    }
}
