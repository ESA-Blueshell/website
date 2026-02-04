package net.blueshell.api.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.base.dto.BaseDTO
import java.io.Serial

@Schema(name = "JwtRequest")
data class JwtRequest(
    var username: String?,
    var password: String?
) : BaseDTO() {
    companion object {
        @Serial
        private const val serialVersionUID = 5926468583005150707L
    }
}
