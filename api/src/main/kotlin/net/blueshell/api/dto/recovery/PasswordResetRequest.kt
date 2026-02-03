package net.blueshell.api.dto.recovery

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.base.BaseDTO
@Schema(name = "PasswordResetRequest")
class PasswordResetRequest : BaseDTO() {
    @NotBlank
    val token: @NotBlank String? = null

    @NotBlank
    @Size(min = 8, max = 100, message = "Password must be at least 8 characters")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    )
    val password: @NotBlank @Size(
        min = 8,
        max = 100,
        message = "Password must be at least 8 characters"
    ) @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)"
    ) String? = null
}
