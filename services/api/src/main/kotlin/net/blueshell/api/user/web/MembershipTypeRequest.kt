package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import net.blueshell.api.shared.enums.MemberType
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "MembershipTypeRequest")
data class MembershipTypeRequest(
    @field:NotNull(message = "Pick a membership type.")
    var type: MemberType? = null,
    @field:NotNull(message = "Fill in the day this type starts.")
    @field:PastOrPresent(message = "A membership type cannot start after today.")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var since: LocalDate? = null,
)
