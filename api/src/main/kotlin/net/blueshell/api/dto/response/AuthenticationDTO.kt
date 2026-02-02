package net.blueshell.api.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.Role
import java.io.Serial
import java.util.function.ToIntFunction

@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true, callSuper = true)
@Schema(name = "Login")
class AuthenticationDTO(
    @field:NotBlank private val token: @NotBlank String?,
    @field:NotBlank private val userId: @NotBlank Long,
    @field:NotBlank private val username: @NotBlank String?,
    @field:NotBlank private val expiration: @NotBlank Long,
    @field:NotEmpty private val roles: @NotEmpty MutableSet<Role?>?,
    private val addressId: Long?
) : BaseDTO() {
    @get:JsonProperty("roles")
    val rolesSorted: MutableList<Role?>
        get() {
            if (roles == null || roles.isEmpty()) return ArrayList<Role?>()

            return roles.stream()
                .sorted(Comparator.comparingInt<Role?>(ToIntFunction { obj: Role? -> obj!!.ordinal }))
                .toList()
        }

    companion object {
        @Serial
        private val serialVersionUID = -8091879091924046844L
    }
}
