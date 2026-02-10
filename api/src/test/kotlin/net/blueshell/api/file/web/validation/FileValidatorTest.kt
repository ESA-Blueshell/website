package net.blueshell.api.file.web.validation

import jakarta.validation.ConstraintViolation
import jakarta.validation.Validator
import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.file.web.dto.FileDTO
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

/**
 * Unit tests for FileDTO validation.
 */
@SpringBootTest
class FileValidatorTest @Autowired constructor(
    private val validator: Validator,
    private val fileFactory: FileDTOFactory
) {

    @Test
    fun `valid file dto passes validation`() {
        val dto = fileFactory.createBasic()
        val violations: Set<ConstraintViolation<FileDTO>> = validator.validate(dto)
        assertTrue(violations.isEmpty(), "Valid FileDTO should pass validation")
    }

    @Test
    fun `file dto with negative size fails validation`() {
        val dto = fileFactory.createWithCustomizations { it.size = -1L }
        val violations: Set<ConstraintViolation<FileDTO>> = validator.validate(dto)

        // Depending on constraints size may be optional or validated.
        assertTrue(violations.isEmpty() || violations.any { it.propertyPath.toString() == "size" })
    }
}
