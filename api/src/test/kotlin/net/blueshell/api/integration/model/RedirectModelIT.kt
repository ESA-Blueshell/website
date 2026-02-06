package net.blueshell.api.integration.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class RedirectModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_column() {
            val telemetry = persist(telemetryFactory.createBasic())
            val redirect = redirectFactory.createBasic()
            redirect.telemetry = telemetry

            val found = persistAndReload(redirect, Redirect::class.java) { it.id }

            assertEquals(telemetry.id, found.telemetry.id)
        }
    }
}
