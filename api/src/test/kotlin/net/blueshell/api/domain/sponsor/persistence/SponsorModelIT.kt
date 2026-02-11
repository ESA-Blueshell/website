package net.blueshell.api.domain.sponsor.persistence

import junit.framework.TestCase.assertEquals
import net.blueshell.api.domain.sponsor.web.mapping.asDto
import net.blueshell.api.shared.model.ModelPersistenceTestSupport
import org.junit.jupiter.api.Assertions
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
            sponsor.picture = persist(fileWithUploader(fileFactory.createImage()))

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

            Assertions.assertEquals(picture.id, found.pictureId)
            Assertions.assertEquals(picture.id, found.picture.id)
        }
    }

    @Nested
    inner class AsDto {
        @Test
        fun `maps persisted sponsor`() {
            val sponsor = sponsorFactory.createBasic().apply {
                picture = persist(fileWithUploader(picture))
            }
            val saved = persist(sponsor)
            entityManager.flush()
            entityManager.clear()

            val reloaded = entityManager.find(Sponsor::class.java, saved.id)
            val dto = reloaded.asDto()

            Assertions.assertEquals(reloaded.id, dto.id)
            Assertions.assertEquals(reloaded.name, dto.name)
            Assertions.assertEquals(reloaded.description, dto.description)
        }
    }
}