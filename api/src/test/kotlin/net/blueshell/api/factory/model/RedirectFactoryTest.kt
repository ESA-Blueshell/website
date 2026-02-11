package net.blueshell.api.factory.model

import net.blueshell.api.domain.telemetry.persistence.Redirect
import org.junit.jupiter.api.Test

class RedirectFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable redirect`() {
        val telemetry = persist(telemetryFactory.createBasic())
        val redirect = redirectFactory.createBasic()
        redirect.telemetry = telemetry

        val saved = persist(redirect)
        assertPersisted(Redirect::class.java, saved.id)
    }
}
