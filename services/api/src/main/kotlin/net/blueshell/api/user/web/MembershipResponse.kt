package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.MemberType
import java.time.Instant
import java.time.LocalDate

@Schema(name = "MembershipResponse")
data class MembershipResponse(
    var userId: Long,
    @field:Schema(description = "The type in force today, the last of types.")
    var memberType: MemberType,
    @field:Schema(description = "Every membership type this membership has had, earliest first; the first starts with it.")
    var types: List<MembershipTypeResponse>,
    var startDate: LocalDate,
    var endDate: LocalDate? = null,
    @field:Schema(description = "Running and waiting for its first contribution, so it carries no member role yet.")
    var pending: Boolean,
    var activatedOn: LocalDate?,
    var version: Long,
    var id: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)

@Schema(name = "MembershipTypeResponse")
data class MembershipTypeResponse(
    var id: Long,
    var type: MemberType,
    var since: LocalDate,
)
