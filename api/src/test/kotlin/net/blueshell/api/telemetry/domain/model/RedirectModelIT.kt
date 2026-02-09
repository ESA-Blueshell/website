package net.blueshell.api.telemetry.domain.model

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.telemetry.domain.model.Redirect
import net.blueshell.api.telemetry.domain.model.Telemetry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RedirectModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists telemetry relation when setting entity`() {
            val telemetry = persist(telemetryFactory.createBasic())
            val redirect = redirectFactory.createBasic()
            redirect.telemetry = telemetry

            val found = persistAndReload(redirect, Redirect::class.java) { it.id }

            assertEquals(telemetry.id, found.telemetry.id)
        }

        @Test
        fun `persists telemetry relation when setting id`() {
            val telemetry = persist(telemetryFactory.createBasic())
            val redirect = redirectFactory.createBasic()
            redirect.telemetry = entityManager.getReference(Telemetry::class.java, telemetry.id)

            val found = persistAndReload(redirect, Redirect::class.java) { it.id }

            assertEquals(telemetry.id, found.telemetry.id)
        }
    }
}
