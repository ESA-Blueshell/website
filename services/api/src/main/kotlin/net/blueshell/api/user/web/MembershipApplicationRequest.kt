package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import net.blueshell.api.user.api.MembershipConditions

@Schema(name = "MembershipApplicationRequest")
data class MembershipApplicationRequest(
    // An omitted acceptance reads as false, so it meets the same refusal as an explicit one.
    @field:AssertTrue(message = MembershipConditions.NOT_ACCEPTED_MESSAGE)
    var conditionsAccepted: Boolean = false,
)
