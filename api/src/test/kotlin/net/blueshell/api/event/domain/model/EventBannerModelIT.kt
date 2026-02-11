package net.blueshell.api.event.persistence

import net.blueshell.api.event.web.mapping.asDto
import net.blueshell.api.file.persistence.File
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
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
            banner.event = event
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
            banner.file = file

            val found = persistAndReload(banner, EventBanner::class.java) { it.id }

            assertEquals(file.id, found.fileId)
            assertEquals(file.id, found.file.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted banner`() {
            val event = persistEvent()
            val file = persist(fileWithUploader(fileFactory.createImage()))
            val banner = persist(eventBannerFactory.createBasic().apply {
                this.event = event
                this.file = file
            })

            val dto = banner.asDto()

            assertEquals(file.id, dto.file?.id)
        }
    }
}
