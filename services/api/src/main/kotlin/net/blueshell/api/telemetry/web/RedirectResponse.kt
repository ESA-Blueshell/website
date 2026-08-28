package net.blueshell.api.telemetry.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "RedirectResponse")
data class RedirectResponse(
    var path: String
)
