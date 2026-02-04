package net.blueshell.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import net.blueshell.api.base.dto.AuditedAutoIdDTO
import net.blueshell.api.common.enums.MemberType
import net.blueshell.api.validation.date.Today
import net.blueshell.api.validation.group.Administration
import net.blueshell.api.validation.group.Creation
import net.blueshell.api.validation.membership.NoExistingMembershipForUserId
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "Membership")
data class MembershipDTO(
    @field:NotNull
    @field:NoExistingMembershipForUserId
    var userId: Long? = null,

    @field:NotNull(groups = [Administration::class])
    var memberType: MemberType? = null,

    @field:NotNull(groups = [Creation::class])
    var city: String? = null,

    @field:NotNull(groups = [Creation::class])
    var country: String? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @field:PastOrPresent(groups = [Administration::class])
    @field:Today(groups = [Creation::class])
    var startDate: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var endDate: LocalDate? = null,

    @field:JsonProperty
    @field:NotNull
    var incasso: Boolean = false
) : AuditedAutoIdDTO()
