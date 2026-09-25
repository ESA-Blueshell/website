package net.blueshell.api.user.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "NameOnRostersRequest")
data class NameOnRostersRequest(
    val shown: Boolean,
)
