package net.blueshell.api.validation.file

import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import org.springframework.web.multipart.MultipartFile

class FileSizeValidator : ConstraintValidator<FileSize?, MultipartFile?> {
    private var min: Long = 0
    private var max: Long = 0
    private var allowEmpty = false

    override fun initialize(constraintAnnotation: FileSize) {
        this.min = constraintAnnotation.min
        this.max = constraintAnnotation.max
        this.allowEmpty = constraintAnnotation.allowEmpty
    }

    override fun isValid(file: MultipartFile?, ctx: ConstraintValidatorContext?): Boolean {
        if (file == null) return true

        if (file.isEmpty) {
            return allowEmpty
        }

        val size = file.size
        return size >= min && size <= max
    }
}