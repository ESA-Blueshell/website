package net.blueshell.api.domain.event.application

import jakarta.validation.constraints.NotNull

data class EventBannerData(
    @field:NotNull(message = "File ID is required")
    var fileId: Long
)
