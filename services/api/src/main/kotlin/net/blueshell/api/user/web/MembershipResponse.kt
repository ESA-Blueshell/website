package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.MemberType
import java.time.Instant
import java.time.LocalDate

@Schema(name = "MembershipResponse")
data class MembershipResponse(
    var userId: Long,
    var memberType: MemberType,
    var startDate: LocalDate,
    var endDate: LocalDate? = null,
    var incasso: Boolean,
    @field:Schema(description = "Running and waiting for its first contribution, so it carries no member role yet.")
    var pending: Boolean,
    var activatedOn: LocalDate?,
    var version: Long,
    var id: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)
