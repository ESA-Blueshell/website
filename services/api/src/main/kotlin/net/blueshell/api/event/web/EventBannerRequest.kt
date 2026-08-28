package net.blueshell.api.event.web

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "EventBannerRequest")
data class EventBannerRequest(
    var fileId: Long,

    var version: Long? = null
)
