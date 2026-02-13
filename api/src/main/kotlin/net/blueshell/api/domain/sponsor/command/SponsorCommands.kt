package net.blueshell.api.domain.sponsor.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import net.blueshell.api.domain.sponsor.persistence.Sponsor
import net.blueshell.api.shared.command.Command

class FindSponsorsCommand : Command<MutableList<Sponsor>>

data class CreateSponsorCommand(
    @field:NotBlank(message = "Sponsor name is required")
    @field:Size(min = 1, max = 200, message = "Name must be 1-200 characters")
    val name: String?,
    @field:NotBlank(message = "Description is required")
    @field:Size(min = 1, max = 5000, message = "Description must be 1-5000 characters")
    val description: String?
) : Command<Sponsor>

data class UpdateSponsorCommand(
    @field:NotNull(message = "Sponsor ID is required")
    @field:Positive(message = "Sponsor ID must be positive")
    val id: Long?,
    @field:NotBlank(message = "Sponsor name is required")
    @field:Size(min = 1, max = 200, message = "Name must be 1-200 characters")
    val name: String?,
    @field:NotBlank(message = "Description is required")
    @field:Size(min = 1, max = 5000, message = "Description must be 1-5000 characters")
    val description: String?,
    val version: Long?
) : Command<Sponsor>

data class FindSponsorByIdCommand(
    @field:NotNull(message = "Sponsor ID is required")
    @field:Positive(message = "Sponsor ID must be positive")
    val id: Long?
) : Command<Sponsor>

data class DeleteSponsorByIdCommand(
    @field:NotNull(message = "Sponsor ID is required")
    @field:Positive(message = "Sponsor ID must be positive")
    val id: Long?
) : Command<Unit>
