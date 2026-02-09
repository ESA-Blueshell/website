package net.blueshell.api.file.validation

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import java.util.*
import java.util.stream.Collectors

class AllowedContentTypesValidator :
    ConstraintValidator<AllowedContentTypes, MultipartFile?> {
    private var allowed: MutableList<MediaType?>? = null

    override fun initialize(constraintAnnotation: AllowedContentTypes) {
        this.allowed = Arrays.stream(constraintAnnotation.value)
            .map { mediaType: String? -> MediaType.parseMediaType(mediaType) }
            .collect(Collectors.toList())
    }

    override fun isValid(file: MultipartFile?, context: ConstraintValidatorContext): Boolean {
        // Let @NotNull/@NotEmpty handle requiredness
        if (file == null || file.isEmpty) {
            return true
        }

        val ct = file.contentType
        if (ct == null || ct.isBlank()) {
            return false
        }

        try {
            val actual = MediaType.parseMediaType(ct)
            return allowed!!.stream().anyMatch { a: MediaType? -> a!!.includes(actual) }
        } catch (e: IllegalArgumentException) {
            return false // Invalid media type format
        }
    }
}