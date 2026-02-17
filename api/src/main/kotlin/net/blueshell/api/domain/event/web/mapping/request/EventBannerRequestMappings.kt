package net.blueshell.api.domain.event.web.mapping.request

import net.blueshell.api.domain.event.command.EventBannerData
import net.blueshell.api.domain.event.web.dto.request.EventBannerRequest
import tech.mappie.api.ObjectMappie

object EventBannerRequestToDataMapper : ObjectMappie<EventBannerRequest, EventBannerData>() {
    override fun map(from: EventBannerRequest) = mapping {
        EventBannerData::fileId fromValue from.fileId!!
    }
}

fun EventBannerRequest.asDomainData(): EventBannerData =
    EventBannerRequestToDataMapper.map(this)
