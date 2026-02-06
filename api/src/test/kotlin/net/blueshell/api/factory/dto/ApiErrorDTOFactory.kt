package net.blueshell.api.factory.dto

import net.blueshell.api.dto.error.ApiErrorDTO
import org.springframework.stereotype.Component
import java.net.URI

/**
 * Factory for ApiErrorDTO test instances.
 */
@Component
class ApiErrorDTOFactory(
    private val fieldFactory: FieldValidationErrorDTOFactory
) : BaseDtoFactory<ApiErrorDTO>() {

    override fun targetType(): Class<ApiErrorDTO> = ApiErrorDTO::class.java

    override fun createBasic(): ApiErrorDTO {
        val dto = ApiErrorDTO()
        dto.type = "about:blank"
        dto.title = "Bad Request"
        dto.status = 400
        dto.detail = "Validation failed"
        dto.instance = URI.create("/api/v1/test")
        dto.errors = listOf(fieldFactory.createBasic())
        dto.traceId = unique("trace")
        return dto
    }
}
