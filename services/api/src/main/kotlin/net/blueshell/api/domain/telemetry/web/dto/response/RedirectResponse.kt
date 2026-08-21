package net.blueshell.api.domain.telemetry.web.dto.response

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "RedirectResponse")
data class RedirectResponse(
    var path: String
)
