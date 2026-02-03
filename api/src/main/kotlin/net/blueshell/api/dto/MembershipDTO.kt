package net.blueshell.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import net.blueshell.api.base.BaseDTO
import net.blueshell.api.common.enums.MemberType
import net.blueshell.api.validation.date.Today
import net.blueshell.api.validation.group.Administration
import net.blueshell.api.validation.group.Creation
import net.blueshell.api.validation.membership.NoExistingMembershipForUserId
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate
@Schema(name = "Membership")
class MembershipDTO : BaseDTO() {
    @NotNull
    @NoExistingMembershipForUserId
    val userId: @NotNull Long? = null

    @NotNull(groups = [Administration::class])
    val memberType: @NotNull(groups = [Administration::class]) MemberType? = null

    @NotNull(groups = [Creation::class])
    val city: @NotNull(groups = [Creation::class]) String? = null

    @NotNull(groups = [Creation::class])
    val country: @NotNull(groups = [Creation::class]) String? = null

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @PastOrPresent(groups = [Administration::class])
    @Today(groups = [Creation::class])
    val startDate: @PastOrPresent(groups = [Administration::class]) LocalDate? = null

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    val endDate: LocalDate? = null

    @JsonProperty
    @NotNull
    val incasso: @NotNull Boolean = false
}
