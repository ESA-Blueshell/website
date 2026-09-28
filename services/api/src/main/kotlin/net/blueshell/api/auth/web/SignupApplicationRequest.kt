package net.blueshell.api.auth.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue
import net.blueshell.api.user.api.MembershipConditions

@Schema(name = "SignupApplicationRequest")
data class SignupApplicationRequest(
    // An omitted acceptance reads as false, so it meets the same refusal as an explicit one.
    @field:AssertTrue(message = MembershipConditions.NOT_ACCEPTED_MESSAGE)
    var conditionsAccepted: Boolean = false,
)
