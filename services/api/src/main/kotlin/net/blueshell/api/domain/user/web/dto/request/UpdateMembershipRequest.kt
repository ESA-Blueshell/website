package net.blueshell.api.domain.user.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import net.blueshell.api.shared.enums.MemberType
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "UpdateMembershipRequest")
data class UpdateMembershipRequest(
    var userId: Long,

    var memberType: MemberType? = null,

    @field:NotNull(message = "Start date is required")
    @field:PastOrPresent(message = "Start date cannot be in the future")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var startDate: LocalDate? = null,

    @field:PastOrPresent(message = "End date cannot be in the future")
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var endDate: LocalDate? = null,

    var incasso: Boolean? = null,

    var version: Long
)
