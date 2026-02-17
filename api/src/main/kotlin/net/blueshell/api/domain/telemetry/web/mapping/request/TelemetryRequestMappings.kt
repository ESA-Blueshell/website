package net.blueshell.api.domain.telemetry.web.mapping.request

import net.blueshell.api.domain.telemetry.command.CreateTelemetryCommand
import net.blueshell.api.domain.telemetry.web.dto.request.CreateTelemetryRequest
import tech.mappie.api.ObjectMappie

object CreateTelemetryRequestToCommandMapper : ObjectMappie<CreateTelemetryRequest, CreateTelemetryCommand>() {
    override fun map(from: CreateTelemetryRequest) = mapping {
        CreateTelemetryCommand::platform fromValue from.platform!!
        CreateTelemetryCommand::url fromValue from.url!!
    }
}

fun CreateTelemetryRequest.asCommand(): CreateTelemetryCommand = CreateTelemetryRequestToCommandMapper.map(this)
