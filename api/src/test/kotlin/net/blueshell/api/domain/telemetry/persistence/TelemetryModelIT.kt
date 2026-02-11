package net.blueshell.api.domain.telemetry.persistence

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import net.blueshell.api.domain.telemetry.web.mapping.asDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class TelemetryModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val telemetry = telemetryFactory.createBasic()
            telemetry.platform = PlatformType.FACEBOOK
            telemetry.url = unique("url")
            val saved = persistAndReload(telemetry, Telemetry::class.java) { it.id }

            assertEquals(telemetry.platform, saved.platform)
            assertEquals(telemetry.url, saved.url)
        }

        @Test
        fun `persists redirects relation when setting entity`() {
            val telemetry = telemetryFactory.createBasic()
            telemetry.platform = PlatformType.FACEBOOK
            telemetry.url = unique("url")
            val saved = persist(telemetry)

            val redirectOne = redirectFactory.createBasic()
            redirectOne.telemetry = saved
            val redirectTwo = redirectFactory.createBasic()
            redirectTwo.telemetry = saved

            persist(redirectOne)
            persist(redirectTwo)
            entityManager.flush()
            entityManager.clear()

            val found = requireNotNull(entityManager.find(Telemetry::class.java, saved.id))
            assertEquals(2, found.redirects.size)
        }

        @Test
        fun `persists redirects relation when setting id`() {
            val telemetry = telemetryFactory.createBasic()
            telemetry.platform = PlatformType.FACEBOOK
            telemetry.url = unique("url")
            val saved = persist(telemetry)

            val redirectOne = redirectFactory.createBasic()
            redirectOne.telemetry = entityManager.getReference(Telemetry::class.java, saved.id)
            val redirectTwo = redirectFactory.createBasic()
            redirectTwo.telemetry = entityManager.getReference(Telemetry::class.java, saved.id)

            persist(redirectOne)
            persist(redirectTwo)
            entityManager.flush()
            entityManager.clear()

            val found = requireNotNull(entityManager.find(Telemetry::class.java, saved.id))
            assertEquals(2, found.redirects.size)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted telemetry`() {
            val telemetry = persist(telemetryFactory.createBasic())

            val dto = telemetry.asDto()

            assertEquals(telemetry.id, dto.id)
            assertEquals(telemetry.url, dto.url)
            assertEquals(telemetry.platform, dto.platform)
        }

        @Test
        fun `maps persisted telemetry for redirect`() {
            val telemetry = persist(telemetryFactory.createBasic())

            val dto = telemetry.asDto()

            assertEquals(telemetry.id, dto.id)
            assertEquals(telemetry.url, dto.url)
            assertEquals(telemetry.platform, dto.platform)
        }
    }
}
