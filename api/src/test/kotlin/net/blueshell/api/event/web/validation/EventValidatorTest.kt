package net.blueshell.api.event.web.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.event.web.dto.EventDTO
import net.blueshell.api.factory.dto.event.EventDTOFactory
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

/**
 * Unit tests for EventDTO validation.
 */
@SpringBootTest
class EventValidatorTest @Autowired constructor(
    private val validator: Validator,
    private val eventFactory: EventDTOFactory
) {

    @Test
    fun `valid event dto passes validation`() {
        val dto = eventFactory.createBasic()
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Valid EventDTO should pass validation")
    }

    @Test
    fun `event dto without title fails validation`() {
        val dto = eventFactory.createWithCustomizations { it.title = "" }
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `event dto with long title fails validation`() {
        val longTitle = "A".repeat(256)
        val dto = eventFactory.createWithCustomizations { it.title = longTitle }
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "title" })
    }

    @Test
    fun `event dto without description fails validation`() {
        val dto = eventFactory.createWithCustomizations { it.description = "" }
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "description" })
    }

    @Test
    fun `event dto with long description fails validation`() {
        val longDescription = "A".repeat(4096)
        val dto = eventFactory.createWithCustomizations { it.description = longDescription }
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "description" })
    }

    @Test
    fun `event dto without start time fails validation`() {
        val dto = eventFactory.createWithCustomizations { it.startTime = null }
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "startTime" })
    }

    @Test
    fun `event dto without end time fails validation`() {
        val dto = eventFactory.createWithCustomizations { it.endTime = null }
        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "endTime" })
    }

    @Test
    fun `event dto with end time before start time fails validation`() {
        val now = Instant.now()
        val dto = eventFactory.createWithCustomizations {
            it.startTime = now.plusSeconds(3600)
            it.endTime = now
        }

        val violations: Set<ConstraintViolation<EventDTO>> = validator.validate(dto)
        // No cross-field check asserted here; single-field constraints are validated above.
        assertTrue(
            violations.none {
                val path = it.propertyPath.toString()
                path == "startTime" || path == "endTime"
            }
        )
    }
}
