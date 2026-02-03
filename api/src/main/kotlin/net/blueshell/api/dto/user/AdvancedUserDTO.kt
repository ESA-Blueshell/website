package net.blueshell.api.dto.user

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.common.enums.Role
import java.sql.Date
import java.time.Instant
import java.util.function.ToIntFunction
@Schema(name = "AdvancedUser")
class AdvancedUserDTO : SimpleUserDTO() {
    val roles: MutableSet<Role?>? = null

    @NotNull
    val dateOfBirth: @NotNull Date? = null

    @NotBlank
    val nationality: @NotBlank String? = null

    @NotNull
    val photoConsent: @NotNull Boolean = false

    @NotNull
    val ehbo: @NotNull Boolean = false

    @NotNull
    val bhv: @NotNull Boolean = false
    val enabled = false
    val gender: String? = null
    val studentNumber: String? = null

    @get:JsonProperty("roles")
    val rolesSorted: MutableList<Role?>
        get() {
            if (roles == null || roles.isEmpty()) return ArrayList<Role?>()

            return roles.stream()
                .sorted(Comparator.comparingInt<Role?>(ToIntFunction { obj: Role? -> obj!!.ordinal }))
                .toList()
        }
}