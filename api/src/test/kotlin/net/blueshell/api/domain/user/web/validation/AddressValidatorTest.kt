package net.blueshell.api.domain.user.web.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.factory.dto.AddressDTOFactory
import net.blueshell.api.domain.user.web.dto.AddressDTO
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Unit tests for AddressDTO validation.
 */
@SpringBootTest
class AddressValidatorTest @Autowired constructor(
    private val validator: Validator,
    private val addressFactory: AddressDTOFactory
) {

    @Test
    fun `valid address dto passes validation`() {
        val dto = addressFactory.createBasic()
        val violations: Set<ConstraintViolation<AddressDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Valid AddressDTO should pass validation")
    }

    @Test
    fun `address dto with invalid country code fails validation`() {
        val dto = addressFactory.createWithCustomizations { it.country = "INVALID" }
        val violations: Set<ConstraintViolation<AddressDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "country" })
    }

    @Test
    fun `address dto with empty city fails validation`() {
        val dto = addressFactory.createWithCustomizations { it.city = "" }
        val violations: Set<ConstraintViolation<AddressDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "city" })
    }

    @Test
    fun `address dto with empty street fails validation`() {
        val dto = addressFactory.createWithCustomizations { it.street = "" }
        val violations: Set<ConstraintViolation<AddressDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "street" })
    }

    @Test
    fun `address dto with empty house number fails validation`() {
        val dto = addressFactory.createWithCustomizations { it.houseNumber = "" }
        val violations: Set<ConstraintViolation<AddressDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "houseNumber" })
    }

    @Test
    fun `address dto with empty zip code fails validation`() {
        val dto = addressFactory.createWithCustomizations { it.zipCode = "" }
        val violations: Set<ConstraintViolation<AddressDTO>> = validator.validate(dto)
        assertFalse(violations.isEmpty())
        assertTrue(violations.any { it.propertyPath.toString() == "zipCode" })
    }
}
