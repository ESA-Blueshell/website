package net.blueshell.api.domain.user.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.shared.enums.MemberType
import java.time.Instant
import java.time.LocalDate

@Schema(name = "MembershipResponse")
data class MembershipResponse(
    var userId: Long? = null,
    var memberType: MemberType? = null,
    var startDate: LocalDate? = null,
    var endDate: LocalDate? = null,
    var incasso: Boolean? = null,
    var version: Long,
    var id: Long,
    var createdAt: Instant,
    var updatedAt: Instant
)
