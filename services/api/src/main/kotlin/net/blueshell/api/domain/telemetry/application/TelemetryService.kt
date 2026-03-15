package net.blueshell.api.domain.telemetry.application

import net.blueshell.api.domain.telemetry.application.exception.TelemetryNotFoundException
import net.blueshell.api.domain.telemetry.persistence.Telemetry
import net.blueshell.api.domain.telemetry.persistence.repository.TelemetryRepository
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelemetryService @Autowired constructor(repository: TelemetryRepository, events: ApplicationEventPublisher) :
    BaseModelService<Telemetry, Long, TelemetryRepository>(repository) {

    @Transactional(readOnly = true)
    override fun findById(id: Long): Telemetry {
        return repository.findById(id)
            .orElseThrow { TelemetryNotFoundException(id) }
    }

    @Transactional
    fun createTelemetry(platform: PlatformType, url: String): Telemetry {
        val telemetry = Telemetry(platform, url)
        create(telemetry)
        return telemetry
    }
}
