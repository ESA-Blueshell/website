package net.blueshell.api.domain.telemetry.web.mapping.request

import net.blueshell.api.domain.telemetry.command.CreateTelemetryCommand
import net.blueshell.api.domain.telemetry.web.dto.request.CreateTelemetryRequest

fun CreateTelemetryRequest.asCommand(): CreateTelemetryCommand =
    CreateTelemetryCommand(
        platform = this.platform!!,
        url = this.url!!,
    )
