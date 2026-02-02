package net.blueshell.api.dto.user

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Size
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.dto.PersonalInfoDTO
import net.blueshell.api.validation.group.Administration
import net.blueshell.api.validation.group.Creation
import net.blueshell.api.validation.group.Update
import net.blueshell.api.validation.user.UniqueUser

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "SimpleUser")
@UniqueUser(groups = [Update::class, Creation::class, Administration::class])
open class SimpleUserDTO : PersonalInfoDTO() {
    private val id: Long? = null

    private val fullName: String? = null

    @NotBlank
    private val initials: @NotBlank String? = null

    @NotBlank
    private val firstName: @NotBlank String? = null

    private val prefix: String? = null

    @NotBlank
    private val lastName: @NotBlank String? = null

    @NotBlank
    private val username: @NotBlank String? = null

    @NotNull
    private val newsletter: @NotNull Boolean = false

    @NotBlank(groups = [Creation::class])
    @Size(min = 8, max = 100, groups = [Creation::class])
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
        groups = [Creation::class]
    )
    private val password: @NotBlank(groups = [Creation::class]) @Size(
        min = 8,
        max = 100,
        groups = [Creation::class]
    ) @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]+$",
        message = "Password must contain at least one lowercase letter, one uppercase letter, one number, and one special character (@$!%*?&)",
        groups = [Creation::class]
    ) String? = null

    private val addressId: Long? = null
}