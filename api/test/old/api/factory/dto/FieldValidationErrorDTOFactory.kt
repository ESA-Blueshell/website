package net.blueshell.api.factory.dto

import net.blueshell.api.shared.dto.error.FieldValidationErrorDTO
import org.springframework.stereotype.Component

/**
 * Factory for FieldValidationErrorDTO test instances.
 */
@Component
class FieldValidationErrorDTOFactory : BaseDtoFactory<FieldValidationErrorDTO>() {

    override fun targetType(): Class<FieldValidationErrorDTO> = FieldValidationErrorDTO::class.java

    override fun createBasic(): FieldValidationErrorDTO {
        val error = FieldValidationErrorDTO()
        error.objectName = "requestBody"
        error.field = "email"
        error.rejectedValue = "not-an-email"
        error.message = "must be a well-formed email address"
        error.code = "Email"
        return error
    }
}
