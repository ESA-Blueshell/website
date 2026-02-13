package net.blueshell.api.domain.event.web.dto.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotNull
import net.blueshell.api.shared.dto.BaseDTO

@Schema(name = "EventBannerRequest")
data class EventBannerRequest(
    @field:NotNull
    var fileId: Long? = null,

    var version: Long? = null
) : BaseDTO()
