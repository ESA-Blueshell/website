package net.blueshell.api.event.domain

import jakarta.validation.constraints.NotNull

data class EventBannerData(
    @field:NotNull(message = "File ID is required")
    var fileId: Long
)
