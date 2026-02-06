package net.blueshell.api.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class SponsorModelIT : ModelPersistenceTestSupport() {

    @Nested
    inner class Persistence {

        @Test
        fun persists_columns_and_picture_relation() {
            val picture = persist(fileWithUploader(fileFactory.createImage()))
            val sponsor = sponsorFactory.createBasic()
            sponsor.name = unique("sponsor")
            sponsor.description = "Sponsor description"
            sponsor.picture = picture

            val found = persistAndReload(sponsor, Sponsor::class.java) { it.id }

            assertEquals(sponsor.name, found.name)
            assertEquals(sponsor.description, found.description)
            assertEquals(picture.id, found.pictureId)
        }
    }
}
