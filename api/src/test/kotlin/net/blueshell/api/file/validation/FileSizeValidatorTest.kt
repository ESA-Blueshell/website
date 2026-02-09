package net.blueshell.api.file.validation

import jakarta.validation.ConstraintValidatorContext
import net.blueshell.api.file.validation.FileSize
import net.blueshell.api.file.validation.FileSizeValidator
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.multipart.MultipartFile

/**
 * Unit tests for FileSizeValidator.
 */
@SpringBootTest
class FileSizeValidatorTest {

    private lateinit var validator: FileSizeValidator
    private lateinit var context: ConstraintValidatorContext
    private lateinit var constraint: FileSize

    @BeforeEach
    fun setUp() {
        validator = FileSizeValidator()
        context = mock<ConstraintValidatorContext>()
        constraint = mock<FileSize>()
    }

    @Test
    fun `valid file size within range`() {
        whenever(constraint.min).thenReturn(100L)
        whenever(constraint.max).thenReturn(1000L)
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.size).thenReturn(500L)

        assertTrue(validator.isValid(file, context))
    }

    @Test
    fun `file size too small`() {
        whenever(constraint.min).thenReturn(100L)
        whenever(constraint.max).thenReturn(1000L)
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.size).thenReturn(50L)

        assertFalse(validator.isValid(file, context))
    }

    @Test
    fun `file size too large`() {
        whenever(constraint.min).thenReturn(100L)
        whenever(constraint.max).thenReturn(1000L)
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.size).thenReturn(1500L)

        assertFalse(validator.isValid(file, context))
    }

    @Test
    fun `empty file allowed`() {
        whenever(constraint.min).thenReturn(100L)
        whenever(constraint.max).thenReturn(1000L)
        whenever(constraint.allowEmpty).thenReturn(true)
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(true)

        assertTrue(validator.isValid(file, context))
    }

    @Test
    fun `empty file not allowed`() {
        whenever(constraint.min).thenReturn(100L)
        whenever(constraint.max).thenReturn(1000L)
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(true)

        assertFalse(validator.isValid(file, context))
    }

    @Test
    fun `null file is valid`() {
        whenever(constraint.min).thenReturn(100L)
        whenever(constraint.max).thenReturn(1000L)
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        assertTrue(validator.isValid(null, context))
    }
}
