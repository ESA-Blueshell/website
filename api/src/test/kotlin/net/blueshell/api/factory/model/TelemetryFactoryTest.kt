package net.blueshell.api.factory.model

import org.junit.jupiter.api.Test

class TelemetryFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable telemetry`() {
        val telemetry = telemetryFactory.createBasic()
        val saved = persist(telemetry)
        assertPersisted(net.blueshell.api.model.Telemetry::class.java, saved.id)
    }
}
