package net.blueshell.api.domain.telemetry.application.command

import net.blueshell.api.domain.telemetry.application.TelemetryService
import net.blueshell.api.domain.telemetry.command.CreateTelemetryCommand
import net.blueshell.api.domain.telemetry.command.FindTelemetryByIdCommand
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.shared.command.CommandHandler
import org.springframework.stereotype.Component

@Component
class FindTelemetryByIdHandler(
    private val service: TelemetryService
) : CommandHandler<FindTelemetryByIdCommand, Telemetry> {
    override val commandType = FindTelemetryByIdCommand::class

    override fun handle(command: FindTelemetryByIdCommand): Telemetry {
        return service.findById(command.id)
    }
}

@Component
class CreateTelemetryHandler(
    private val service: TelemetryService
) : CommandHandler<CreateTelemetryCommand, Telemetry> {
    override val commandType = CreateTelemetryCommand::class

    override fun handle(command: CreateTelemetryCommand): Telemetry {
        return service.createTelemetry(command.platform, command.url)
    }
}
