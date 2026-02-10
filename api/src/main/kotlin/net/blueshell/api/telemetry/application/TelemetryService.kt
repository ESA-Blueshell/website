package net.blueshell.api.telemetry.application

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.service.BaseModelService
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.persistence.TelemetryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelemetryService @Autowired constructor(repository: TelemetryRepository, events: ApplicationEventPublisher) :
    BaseModelService<Telemetry, Long, TelemetryRepository>(repository) {
    @Transactional
    fun createTelemetry(platform: PlatformType, url: String): Telemetry {
        val telemetry = Telemetry(platform, url)
        create(telemetry)
        return telemetry
    }
}
