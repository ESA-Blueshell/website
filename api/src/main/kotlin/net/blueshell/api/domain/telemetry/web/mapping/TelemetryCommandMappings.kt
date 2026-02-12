package net.blueshell.api.domain.telemetry.web.mapping

import net.blueshell.api.domain.telemetry.command.CreateTelemetryCommand
import net.blueshell.api.domain.telemetry.web.dto.CreateTelemetryRequest
import tech.mappie.api.ObjectMappie

object CreateTelemetryRequestToCommandMapper : ObjectMappie<CreateTelemetryRequest, CreateTelemetryCommand>() {
    override fun map(from: CreateTelemetryRequest) = mapping {
        CreateTelemetryCommand::platform fromProperty { from.platform!! }
        CreateTelemetryCommand::url fromProperty { from.url!! }
    }
}

fun CreateTelemetryRequest.asCommand(): CreateTelemetryCommand = CreateTelemetryRequestToCommandMapper.map(this)
