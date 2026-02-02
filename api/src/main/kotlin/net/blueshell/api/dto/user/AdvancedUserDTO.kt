package net.blueshell.api.dto.user

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import lombok.Data
import lombok.EqualsAndHashCode
import net.blueshell.api.common.enums.Role
import java.sql.Date
import java.time.Instant
import java.util.function.ToIntFunction

@Data
@EqualsAndHashCode(callSuper = false)
@Schema(name = "AdvancedUser")
class AdvancedUserDTO : SimpleUserDTO() {
    private val roles: MutableSet<Role?>? = null

    @NotNull
    private val dateOfBirth: @NotNull Date? = null

    @NotBlank
    private val nationality: @NotBlank String? = null

    @NotNull
    private val photoConsent: @NotNull Boolean = false

    @NotNull
    private val ehbo: @NotNull Boolean = false

    @NotNull
    private val bhv: @NotNull Boolean = false
    private val enabled = false
    private val createdAt: Instant? = null
    private val gender: String? = null
    private val studentNumber: String? = null

    @get:JsonProperty("roles")
    val rolesSorted: MutableList<Role?>
        get() {
            if (roles == null || roles.isEmpty()) return ArrayList<Role?>()

            return roles.stream()
                .sorted(Comparator.comparingInt<Role?>(ToIntFunction { obj: Role? -> obj!!.ordinal }))
                .toList()
        }
}