package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue

@Schema(name = "MembershipApplicationRequest")
data class MembershipApplicationRequest(
    @field:AssertTrue(message = "The membership conditions must be accepted")
    var conditionsAccepted: Boolean? = null
)
