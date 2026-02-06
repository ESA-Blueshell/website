package net.blueshell.api.integration.model.event

import net.blueshell.api.integration.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class EventBannerModelIT : net.blueshell.api.integration.model.ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_join_columns() {
            val event = persistEvent()
            val file = persist(fileWithUploader(fileFactory.createImage()))

            val banner = eventBannerFactory.createBasic()
            banner.event = event
            banner.file = file

            val found = persistAndReload(banner, EventBanner::class.java) { it.id }

            assertEquals(event.id, found.eventId)
            assertEquals(file.id, found.fileId)
        }
    }
}
