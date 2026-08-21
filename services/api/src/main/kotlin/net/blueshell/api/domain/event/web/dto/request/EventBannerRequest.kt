package net.blueshell.api.domain.event.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "EventBannerRequest")
data class EventBannerRequest(
    var fileId: Long,

    var version: Long? = null
)
