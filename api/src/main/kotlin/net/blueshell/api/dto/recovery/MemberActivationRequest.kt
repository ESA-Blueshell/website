package net.blueshell.api.dto.recovery

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.validation.user.UniqueUsername
@Schema(name = "MemberActivationRequest")
class MemberActivationRequest : BaseDTO() {
    @NotBlank
    val token: @NotBlank String? = null

    @NotBlank
    @UniqueUsername
    val username: @NotBlank String? = null

    @JsonProperty
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    )
    val password: @Size(
        min = 8,
        max = 100,
        message = "Password must be at least 8 characters"
    ) @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    ) String? = null
}
