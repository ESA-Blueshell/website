package net.blueshell.api.domain.telemetry.command

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.PlatformType

data class FindTelemetryByIdCommand(
    @field:NotNull(message = "Telemetry ID is required")
    val id: Long
) : Command<Telemetry>

data class CreateTelemetryCommand(
    @field:NotNull(message = "Platform is required")
    val platform: PlatformType,

    @field:NotBlank(message = "URL cannot be blank")
    val url: String
) : Command<Telemetry>
