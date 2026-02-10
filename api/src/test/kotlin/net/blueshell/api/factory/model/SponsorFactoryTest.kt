package net.blueshell.api.factory.model

import net.blueshell.api.sponsor.persistence.Sponsor
import org.junit.jupiter.api.Test

class SponsorFactoryTest : ModelFactoryTestSupport() {

    @Test
    fun `creates persistable sponsor`() {
        val logo = fileWithUploader(fileFactory.createImage())
        val sponsor = sponsorFactory.createBasic()
        sponsor.picture = persist(logo)

        val saved = persist(sponsor)
        assertPersisted(Sponsor::class.java, saved.id)
    }
}
