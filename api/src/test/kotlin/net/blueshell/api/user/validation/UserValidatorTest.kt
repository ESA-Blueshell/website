package net.blueshell.api.user.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.user.dto.AdvancedUserDTO
import net.blueshell.api.user.dto.SimpleUserDTO
import net.blueshell.api.factory.dto.user.AdvancedUserDTOFactory
import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import net.blueshell.api.shared.validation.group.Creation
import net.blueshell.api.shared.validation.group.Update
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Unit tests for user DTO validation groups.
 */
@SpringBootTest
class UserValidatorTest @Autowired constructor(
    private val validator: Validator,
    private val simpleUserFactory: SimpleUserDTOFactory,
    private val advancedUserFactory: AdvancedUserDTOFactory
) {

    @Test
    fun `valid simple user dto passes validation`() {
        val dto = simpleUserFactory.createBasic()
        val violations: Set<ConstraintViolation<SimpleUserDTO>> = validator.validate(dto, Creation::class.java)
        assertTrue(violations.isEmpty(), "Valid SimpleUserDTO should pass validation")
    }

    @Test
    fun `simple user dto without password fails creation validation`() {
        val dto = simpleUserFactory.createWithCustomizations { it.password = null }
        val violations: Set<ConstraintViolation<SimpleUserDTO>> = validator.validate(dto, Creation::class.java)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "password" })
    }

    @Test
    fun `simple user dto with weak password fails validation`() {
        val dto = simpleUserFactory.createWithCustomizations { it.password = "weak" }
        val violations: Set<ConstraintViolation<SimpleUserDTO>> = validator.validate(dto, Creation::class.java)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "password" })
    }

    @Test
    fun `simple user dto with invalid email fails validation`() {
        val dto = simpleUserFactory.createWithCustomizations { it.email = "invalid-email" }
        val violations: Set<ConstraintViolation<SimpleUserDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "email" })
    }

    @Test
    fun `simple user dto with empty username fails validation`() {
        val dto = simpleUserFactory.createWithCustomizations { it.username = "" }
        val violations: Set<ConstraintViolation<SimpleUserDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "username" })
    }

    @Test
    fun `valid advanced user dto passes validation`() {
        val dto = advancedUserFactory.createBasic()
        val violations: Set<ConstraintViolation<AdvancedUserDTO>> = validator.validate(dto, Update::class.java)
        assertTrue(violations.isEmpty(), "Valid AdvancedUserDTO should pass validation")
    }

    @Test
    fun `advanced user dto without date of birth fails validation`() {
        val dto = advancedUserFactory.createWithCustomizations { it.dateOfBirth = null }
        val violations: Set<ConstraintViolation<AdvancedUserDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "dateOfBirth" })
    }

    @Test
    fun `advanced user dto without nationality fails validation`() {
        val dto = advancedUserFactory.createWithCustomizations { it.nationality = null }
        val violations: Set<ConstraintViolation<AdvancedUserDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "nationality" })
    }
}
