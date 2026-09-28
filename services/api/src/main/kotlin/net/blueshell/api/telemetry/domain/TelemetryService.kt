package net.blueshell.api.telemetry.domain

import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.telemetry.persistence.Telemetry
import net.blueshell.api.telemetry.persistence.TelemetryRepository
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TelemetryService
    @Autowired
    constructor(
        private val repository: TelemetryRepository,
    ) {
        // Read back after each write, so the columns the database fills are on the answer.
        @PersistenceContext
        private lateinit var em: EntityManager

        private fun written(row: Telemetry): Telemetry = repository.saveAndFlush(row).also(em::refresh)

        @Transactional(readOnly = true)
        fun findById(id: Long): Telemetry =
            repository
                .findById(id)
                .orElseThrow { TelemetryNotFoundException(id) }

        @Transactional
        fun createTelemetry(
            platform: PlatformType,
            url: String,
        ): Telemetry {
            val telemetry = Telemetry(platform, url)
            written(telemetry)
            return telemetry
        }
    }
