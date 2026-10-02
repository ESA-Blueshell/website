package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.domain.IncassoStanding
import java.time.Instant
import java.time.LocalDate

@Schema(name = "MembershipResponse")
data class MembershipResponse(
    var userId: Long,
    var memberType: MemberType,
    var startDate: LocalDate,
    var endDate: LocalDate? = null,
    var incasso: Boolean,
    var incassoStanding: IncassoStanding,
    @field:Schema(description = "The country code of the mandate's IBAN, where one is recorded; shown as NL•• … ••34.")
    var ibanCountry: String? = null,
    @field:Schema(description = "The last two characters of the mandate's IBAN, where one is recorded.")
    var ibanLastTwo: String? = null,
    @field:Schema(description = "Running and waiting for its first contribution, so it carries no member role yet.")
    var pending: Boolean,
    var activatedOn: LocalDate?,
    var version: Long,
    var id: Long,
    var createdAt: Instant,
    var updatedAt: Instant,
)
