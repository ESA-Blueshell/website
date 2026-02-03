package net.blueshell.api.service

import net.blueshell.api.base.BaseModelService
import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.model.Telemetry
import net.blueshell.api.repository.TelemetryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelemetryService @Autowired constructor(repository: TelemetryRepository, events: ApplicationEventPublisher) :
    BaseModelService<Telemetry, TelemetryRepository>(repository) {
    @Transactional
    fun createTelemetry(platform: PlatformType, url: String): Telemetry {
        val telemetry = Telemetry(platform, url)
        create(telemetry)
        return telemetry
    }
}
