package net.blueshell.api.domain.membership.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.VersionedDTO
import net.blueshell.api.shared.enums.MemberType
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Schema(name = "UpdateMembershipRequest")
data class UpdateMembershipRequest(
    @field:NotNull
    var userId: Long? = null,

    var memberType: MemberType? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var startDate: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var endDate: LocalDate? = null,

    var incasso: Boolean? = null
) : VersionedDTO()
