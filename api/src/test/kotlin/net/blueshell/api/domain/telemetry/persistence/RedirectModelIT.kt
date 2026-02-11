package net.blueshell.api.domain.telemetry.persistence

import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
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

            Assertions.assertEquals(telemetry.id, found.telemetry.id)
        }
    }
}
