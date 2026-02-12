package net.blueshell.api.domain.telemetry.command

import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.shared.command.Command
import net.blueshell.api.shared.enums.PlatformType

data class FindTelemetryByIdCommand(
    val id: Long
) : Command<Telemetry>

data class CreateTelemetryCommand(
    val platform: PlatformType,
    val url: String
) : Command<Telemetry>
