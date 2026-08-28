package net.blueshell.api.event.web

import net.blueshell.api.event.domain.EventBannerData

fun EventBannerRequest.asDomainData(): EventBannerData =
    EventBannerData(
        fileId = this.fileId!!,
    )
