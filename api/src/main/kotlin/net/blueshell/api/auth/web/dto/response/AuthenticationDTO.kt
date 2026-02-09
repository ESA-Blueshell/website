package net.blueshell.api.auth.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.dto.BaseDTO
import java.io.Serial
import java.util.function.ToIntFunction

@Schema(name = "Login")
data class AuthenticationDTO(
    @field:NotBlank
    var token: String,

    @field:NotNull
    var userId: Long,

    @field:NotBlank
    var username: String,

    @field:NotNull
    var expiration: Long,

    @field:NotEmpty
    var roles: MutableSet<Role> = mutableSetOf(),

    var addressId: Long? = null
) : BaseDTO() {
    @get:JsonProperty("roles")
    val rolesSorted: MutableList<Role>
        get() {
            if (roles.isEmpty()) return ArrayList()

            return roles.stream()
                .sorted(Comparator.comparingInt(ToIntFunction { obj: Role -> obj.ordinal }))
                .toList()
        }

    companion object {
        @Serial
        val serialVersionUID = -8091879091924046844L
    }
}
