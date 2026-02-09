package net.blueshell.api.feature.user.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.Role
import java.sql.Date
import java.util.function.ToIntFunction

@Schema(name = "AdvancedUser")
class AdvancedUserDTO(
    var roles: MutableSet<Role?>? = null,

    @field:NotNull
    var dateOfBirth: Date? = null,

    @field:NotBlank
    var nationality: String? = null,

    @field:NotNull
    var photoConsent: Boolean = false,

    @field:NotNull
    var ehbo: Boolean = false,

    @field:NotNull
    var bhv: Boolean = false,

    var enabled: Boolean = false,
    var gender: String? = null,
    var studentNumber: String? = null
) : SimpleUserDTO() {
    @get:JsonProperty("roles")
    val rolesSorted: MutableList<Role?>
        get() {
            if (roles == null || roles!!.isEmpty()) return mutableListOf()

            return roles!!.stream()
                .sorted(
                    Comparator.comparingInt<Role?>(ToIntFunction { obj: Role? -> obj!!.ordinal })
                )
                .toList()
        }
}
