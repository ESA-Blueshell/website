package net.blueshell.api.domain.file.web.validation

import jakarta.validation.ConstraintValidatorContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.web.multipart.MultipartFile

class AllowedContentTypesValidatorTest {

    private lateinit var validator: AllowedContentTypesValidator
    private val context = mock<ConstraintValidatorContext>()
    private val constraint = mock<AllowedContentTypes>()

    @BeforeEach
    fun setUp() {
        validator = AllowedContentTypesValidator()
    }

    @Test
    fun `accepts matching concrete content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg", "image/png"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("image/jpeg")

        assertThat(validator.isValid(file, context)).isTrue()
    }

    @Test
    fun `accepts matching wildcard content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/*"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("image/webp")

        assertThat(validator.isValid(file, context)).isTrue()
    }

    @Test
    fun `rejects unsupported content type`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("application/pdf")

        assertThat(validator.isValid(file, context)).isFalse()
    }

    @Test
    fun `rejects missing or blank content type for non-empty file`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val nullContentType = mock<MultipartFile>()
        whenever(nullContentType.isEmpty).thenReturn(false)
        whenever(nullContentType.contentType).thenReturn(null)

        val blankContentType = mock<MultipartFile>()
        whenever(blankContentType.isEmpty).thenReturn(false)
        whenever(blankContentType.contentType).thenReturn("  ")

        assertThat(validator.isValid(nullContentType, context)).isFalse()
        assertThat(validator.isValid(blankContentType, context)).isFalse()
    }

    @Test
    fun `rejects invalid media type format`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val file = mock<MultipartFile>()
        whenever(file.isEmpty).thenReturn(false)
        whenever(file.contentType).thenReturn("not/a/type;=")

        assertThat(validator.isValid(file, context)).isFalse()
    }

    @Test
    fun `accepts null or empty file`() {
        whenever(constraint.value).thenReturn(arrayOf("image/jpeg"))
        validator.initialize(constraint)

        val emptyFile = mock<MultipartFile>()
        whenever(emptyFile.isEmpty).thenReturn(true)

        assertThat(validator.isValid(null, context)).isTrue()
        assertThat(validator.isValid(emptyFile, context)).isTrue()
    }
}
