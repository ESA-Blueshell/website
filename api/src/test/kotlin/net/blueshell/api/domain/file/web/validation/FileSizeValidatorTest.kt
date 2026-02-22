package net.blueshell.api.domain.file.web.validation

import jakarta.validation.ConstraintValidatorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile

class FileSizeValidatorTest {

    private lateinit var validator: FileSizeValidator
    private val context = mock<ConstraintValidatorContext>()
    private val constraint = mock<FileSize>()

    @BeforeEach
    fun setUp() {
        validator = FileSizeValidator()
        whenever(constraint.min).thenReturn(100)
        whenever(constraint.max).thenReturn(1000)
    }

    @Test
    fun `accepts file size in inclusive range`() {
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        val minFile = mock<MultipartFile>()
        whenever(minFile.isEmpty).thenReturn(false)
        whenever(minFile.size).thenReturn(100)

        val maxFile = mock<MultipartFile>()
        whenever(maxFile.isEmpty).thenReturn(false)
        whenever(maxFile.size).thenReturn(1000)

        assertThat(validator.isValid(minFile, context)).isTrue()
        assertThat(validator.isValid(maxFile, context)).isTrue()
    }

    @Test
    fun `rejects out of range file size`() {
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        val tooSmall = mock<MultipartFile>()
        whenever(tooSmall.isEmpty).thenReturn(false)
        whenever(tooSmall.size).thenReturn(99)

        val tooLarge = mock<MultipartFile>()
        whenever(tooLarge.isEmpty).thenReturn(false)
        whenever(tooLarge.size).thenReturn(1001)

        assertThat(validator.isValid(tooSmall, context)).isFalse()
        assertThat(validator.isValid(tooLarge, context)).isFalse()
    }

    @Test
    fun `accepts or rejects empty file based on allowEmpty`() {
        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(true)

        whenever(constraint.allowEmpty).thenReturn(true)
        validator.initialize(constraint)
        assertThat(validator.isValid(file, context)).isTrue()

        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)
        assertThat(validator.isValid(file, context)).isFalse()
    }

    @Test
    fun `accepts null file`() {
        whenever(constraint.allowEmpty).thenReturn(false)
        validator.initialize(constraint)

        assertThat(validator.isValid(null, context)).isTrue()
    }
}
