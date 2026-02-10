package net.blueshell.api.membership.web.dto

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.dto.AuditedAutoIdDTO
import net.blueshell.api.shared.validation.date.Today
import net.blueshell.api.shared.validation.group.Administration
import net.blueshell.api.shared.validation.group.Creation
import net.blueshell.api.membership.web.validation.NoExistingMembershipForUserId
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "Membership")
data class MembershipDTO(
    @field:NotNull
    @field:NoExistingMembershipForUserId
    var userId: Long,

    @field:NotNull(groups = [Administration::class])
    var memberType: MemberType,

    @field:NotNull(groups = [Creation::class])
    var city: String? = null,

    @field:NotNull(groups = [Creation::class])
    var country: String? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @field:PastOrPresent(groups = [Administration::class])
    @field:Today(groups = [Creation::class])
    var startDate: LocalDate,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var endDate: LocalDate? = null,

    @field:NotNull
    var incasso: Boolean = false
) : AuditedAutoIdDTO()
