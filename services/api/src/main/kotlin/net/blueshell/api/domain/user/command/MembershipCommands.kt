package net.blueshell.api.domain.user.command

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.model.SignupOutcome

// No @ValidMembership: the interval check needs the account, which only the handler
// can resolve from the token. A signup token exists only for a freshly created
// account, so there is no prior membership to overlap; completeIfReady covers the
// already-a-member case.
data class SubmitSignupApplicationCommand(
    @field:NotBlank(message = "A signup token is required")
    val signupToken: String,
    @field:AssertTrue(message = "The membership conditions must be accepted")
    val conditionsAccepted: Boolean?
) : Command<SignupOutcome>

data class SaveSignupAddressCommand(
    @field:NotBlank(message = "A signup token is required")
    val signupToken: String,
    @field:NotBlank val country: String,
    @field:NotBlank val city: String,
    @field:NotBlank val street: String,
    @field:NotBlank val houseNumber: String,
    @field:NotBlank val zipCode: String
) : Command<Unit>
