package net.blueshell.api.domain.auth.web.dto.response

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.BaseDTO
import net.blueshell.api.shared.enums.Role
import java.io.Serial
import java.util.function.ToIntFunction

@Schema(name = "Login")
data class AuthenticationDTO(
    @field:NotBlank
    var token: String? = null,

    @field:NotNull
    var userId: Long? = null,

    @field:NotBlank
    var username: String? = null,

    @field:NotNull
    var expiration: Long? = null,

    @field:NotEmpty
    var roles: MutableSet<Role>? = null,

    var addressId: Long? = null
) : BaseDTO() {
    @get:JsonProperty("roles")
    val rolesSorted: MutableList<Role>
        get() {
            if (roles == null || roles!!.isEmpty()) return ArrayList()

            return roles!!.stream()
                .sorted(Comparator.comparingInt(ToIntFunction { obj: Role -> obj.ordinal }))
                .toList()
        }

    companion object {
        @Serial
        val serialVersionUID = -8091879091924046844L
    }
}
