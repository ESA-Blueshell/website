package net.blueshell.api.domain.membership.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.enums.MemberType
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "BoardCreateMembershipRequest")
data class BoardCreateMembershipRequest(
    @field:NotNull
    var userId: Long? = null,

    @field:NotNull
    var memberType: MemberType? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var startDate: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var endDate: LocalDate? = null,

    @field:NotNull
    var incasso: Boolean? = null
)
