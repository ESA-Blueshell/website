package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import net.blueshell.api.model.File
import net.blueshell.api.model.event.EventBanner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventBannerModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists event relation when setting entity`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val banner = eventBannerFactory.createBasic()
            banner.event = event
            banner.file = file

            val found = persistAndReload(banner, EventBanner::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists event relation when setting id`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val banner = eventBannerFactory.createBasic()
            banner.eventId = event.id ?: 0
            banner.file = file

            val found = persistAndReload(banner, EventBanner::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(event.id, found.event.id)
        }

        @Test
        fun `persists file relation when setting entity`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val banner = eventBannerFactory.createBasic()
            banner.event = event
            banner.file = file

            val found = persistAndReload(banner, EventBanner::class.java) { it.id }

            assertEquals(file.id, found.fileId)
            assertEquals(file.id, found.file.id)
        }

        @Test
        fun `persists file relation when setting id`() {
            val event = persistEvent()
            val file: File = persist(fileWithUploader(fileFactory.createImage()))

            val banner = eventBannerFactory.createBasic()
            banner.event = event
            banner.fileId = file.id ?: 0

            val found = persistAndReload(banner, EventBanner::class.java) { it.id }

            assertEquals(file.id, found.fileId)
            assertEquals(file.id, found.file.id)
        }
    }
}
