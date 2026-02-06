package net.blueshell.api.integration.model

import net.blueshell.api.common.enums.PlatformType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TelemetryModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_redirect_relation() {
            val telemetry = telemetryFactory.createBasic()
            telemetry.platform = PlatformType.FACEBOOK
            telemetry.url = unique("url")

            val redirect = redirectFactory.createBasic()
            redirect.telemetry = telemetry

            val savedTelemetry = persist(telemetry)
            persist(redirect)
            entityManager.flush()
            entityManager.clear()

            val found = requireNotNull(entityManager.find(Telemetry::class.java, savedTelemetry.id))
            assertEquals(telemetry.platform, found.platform)
            assertEquals(telemetry.url, found.url)
            assertEquals(1, found.redirects.size)
        }
    }
}
