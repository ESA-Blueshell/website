package net.blueshell.api.domain.event.web.mapping.request

import net.blueshell.api.domain.event.command.EventBannerData
import net.blueshell.api.domain.event.web.dto.request.EventBannerRequest

fun EventBannerRequest.asDomainData(): EventBannerData =
    EventBannerData(
        fileId = this.fileId!!,
    )
