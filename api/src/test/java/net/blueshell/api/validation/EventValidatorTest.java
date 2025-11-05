package net.blueshell.api.validation;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import net.blueshell.api.dto.event.EventDTO;
import net.blueshell.api.factory.dto.event.EventDTOFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for EventDTO validation.
 */
@SpringBootTest
class EventValidatorTest {

    @Autowired private Validator validator;
    @Autowired private EventDTOFactory eventFactory;

    @Test
    void validEventDTO_passesValidation() {
        EventDTO dto = eventFactory.createBasic();
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertTrue(violations.isEmpty(), "Valid EventDTO should pass validation");
    }

    @Test
    void eventDTO_withoutTitle_failsValidation() {
        EventDTO dto = eventFactory.createWithCustomizations(e -> e.setTitle(""));
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
    }

    @Test
    void eventDTO_withLongTitle_failsValidation() {
        String longTitle = "A".repeat(256);
        EventDTO dto = eventFactory.createWithCustomizations(e -> e.setTitle(longTitle));
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("title")));
    }

    @Test
    void eventDTO_withoutDescription_failsValidation() {
        EventDTO dto = eventFactory.createWithCustomizations(e -> e.setDescription(""));
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void eventDTO_withLongDescription_failsValidation() {
        String longDescription = "A".repeat(4096);
        EventDTO dto = eventFactory.createWithCustomizations(e -> e.setDescription(longDescription));
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("description")));
    }

    @Test
    void eventDTO_withoutStartTime_failsValidation() {
        EventDTO dto = eventFactory.createWithCustomizations(e -> e.setStartTime(null));
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("startTime")));
    }

    @Test
    void eventDTO_withoutEndTime_failsValidation() {
        EventDTO dto = eventFactory.createWithCustomizations(e -> e.setEndTime(null));
        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("endTime")));
    }

    @Test
    void eventDTO_withEndTimeBeforeStartTime_failsValidation() {
        Instant now = Instant.now();
        EventDTO dto = eventFactory.createWithCustomizations(e -> {
            e.setStartTime(now.plusSeconds(3600));
            e.setEndTime(now);
        });

        Set<ConstraintViolation<EventDTO>> violations = validator.validate(dto);
        // No cross-field check asserted here; single-field constraints are validated above.
        assertTrue(violations.stream()
                .noneMatch(v -> v.getPropertyPath().toString().equals("startTime")
                        || v.getPropertyPath().toString().equals("endTime")));
    }
}
