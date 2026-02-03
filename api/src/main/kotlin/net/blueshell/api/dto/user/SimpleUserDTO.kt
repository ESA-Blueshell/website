package net.blueshell.api.dto.user

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import net.blueshell.api.dto.PersonalInfoDTO
import net.blueshell.api.validation.group.Administration
import net.blueshell.api.validation.group.Creation
import net.blueshell.api.validation.group.Update
import net.blueshell.api.validation.user.UniqueUser
@Schema(name = "SimpleUser")
@UniqueUser(groups = [Update::class, Creation::class, Administration::class])
open class SimpleUserDTO : PersonalInfoDTO() {
    val fullName: String? = null

    @NotBlank
    val initials: @NotBlank String? = null

    @NotBlank
    val firstName: @NotBlank String? = null

    val prefix: String? = null

    @NotBlank
    val lastName: @NotBlank String? = null

    @NotBlank
    val username: @NotBlank String? = null

    @NotNull
    val newsletter: @NotNull Boolean = false

    @NotBlank(groups = [Creation::class])
    @Size(min = 8, max = 100, groups = [Creation::class])
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
        groups = [Creation::class]
    )
    val password: @NotBlank(groups = [Creation::class]) @Size(
        min = 8,
        max = 100,
        groups = [Creation::class]
    ) @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
        groups = [Creation::class]
    ) String? = null

    val addressId: Long? = null
}
