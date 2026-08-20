package net.blueshell.api.domain.auth.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.AssertTrue

@Schema(name = "SignupApplicationRequest")
data class SignupApplicationRequest(
    @field:AssertTrue(message = "The membership conditions must be accepted")
    var conditionsAccepted: Boolean? = null
)
