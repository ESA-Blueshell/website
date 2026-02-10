package net.blueshell.api.factory.model

import net.blueshell.api.telemetry.persistence.Telemetry
import org.junit.jupiter.api.Test

class TelemetryFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable telemetry`() {
        val telemetry = telemetryFactory.createBasic()
        val saved = persist(telemetry)
        assertPersisted(Telemetry::class.java, saved.id)
    }
}
