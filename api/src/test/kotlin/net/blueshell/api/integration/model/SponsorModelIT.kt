package net.blueshell.api.integration.model

import net.blueshell.api.model.Sponsor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SponsorModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun `persists column fields`() {
            val sponsor = sponsorFactory.createBasic()
            sponsor.name = unique("sponsor")
            sponsor.description = "Sponsor description"
            sponsor.pictureId = persist(fileWithUploader(fileFactory.createImage())).id ?: 0

            val found = persistAndReload(sponsor, Sponsor::class.java) { it.id }

            assertEquals(sponsor.name, found.name)
            assertEquals(sponsor.description, found.description)
        }

        @Test
        fun `persists picture relation when setting entity`() {
            val picture = persist(fileWithUploader(fileFactory.createImage()))
            val sponsor = sponsorFactory.createBasic()
            sponsor.name = unique("sponsor")
            sponsor.description = "Sponsor description"
            sponsor.picture = picture

            val found = persistAndReload(sponsor, Sponsor::class.java) { it.id }

            assertEquals(picture.id, found.pictureId)
            assertEquals(picture.id, found.picture.id)
        }

        @Test
        fun `persists picture relation when setting id`() {
            val picture = persist(fileWithUploader(fileFactory.createImage()))
            val sponsor = sponsorFactory.createBasic()
            sponsor.name = unique("sponsor")
            sponsor.description = "Sponsor description"
            sponsor.pictureId = picture.id ?: 0

            val found = persistAndReload(sponsor, Sponsor::class.java) { it.id }

            assertEquals(picture.id, found.pictureId)
            assertEquals(picture.id, found.picture.id)
        }
    }
}
