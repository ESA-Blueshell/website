package net.blueshell.api.sponsor.web.dto

import net.blueshell.api.factory.dto.SponsorDTOFactory
import net.blueshell.api.factory.model.SponsorFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.sponsor.application.SponsorService
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.web.mapping.asEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SponsorDtoIT @Autowired constructor(
    private val sponsorDTOFactory: SponsorDTOFactory,
    private val sponsorFactory: SponsorFactory,
    private val sponsorService: SponsorService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists mapped sponsor`() {
            val sponsor = sponsorFactory.createBasic()
            val picture = persist(fileWithUploader(sponsor.picture))
            sponsor.picture = picture
            val dto = sponsorDTOFactory.createBasic()

            val mapped = dto.asEntity(sponsor)
            val saved = sponsorService.create(mapped)
            flushAndClear()

            val reloaded = reload(Sponsor::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.description).isEqualTo(dto.description)
            assertThat(reloaded.pictureId).isEqualTo(picture.id)
        }
    }
}
