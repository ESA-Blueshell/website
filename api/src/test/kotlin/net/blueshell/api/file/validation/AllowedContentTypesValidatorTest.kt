package net.blueshell.api.file.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.file.validation.AllowedContentTypes
import net.blueshell.api.file.validation.AllowedContentTypesValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.multipart.MultipartFile

/**
 * Unit tests for AllowedContentTypesValidator.
 */
@SpringBootTest
class AllowedContentTypesValidatorTest {

    private lateinit var validator: AllowedContentTypesValidator
    private lateinit var context: ConstraintValidatorContext
    private lateinit var constraint: AllowedContentTypes

    @BeforeEach
    fun setUp() {
        validator = AllowedContentTypesValidator()
        context = mock<ConstraintValidatorContext>()
        constraint = mock<AllowedContentTypes>()
    }

    @Test
    fun `valid content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg", "image/png"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("image/jpeg")

        assertTrue(validator.isValid(file, context))
    }

    @Test
    fun `invalid content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg", "image/png"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("application/pdf")

        assertFalse(validator.isValid(file, context))
    }

    @Test
    fun `wildcard content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/*"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("image/jpeg")

        assertTrue(validator.isValid(file, context))
    }

    @Test
    fun `null content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn(null)

        assertFalse(validator.isValid(file, context))
    }

    @Test
    fun `empty content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("")

        assertFalse(validator.isValid(file, context))
    }

    @Test
    fun `empty file is valid`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(true)

        assertTrue(validator.isValid(file, context))
    }

    @Test
    fun `null file is valid`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        assertTrue(validator.isValid(null, context))
    }
}
