package net.blueshell.api.integration.mapper

import net.blueshell.api.factory.dto.SponsorDTOFactory
import net.blueshell.api.factory.model.SponsorFactory
import net.blueshell.api.model.Sponsor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SponsorMapperIT @Autowired constructor(
    private val sponsorMapper: SponsorMapper,
    private val sponsorDTOFactory: SponsorDTOFactory,
    private val sponsorFactory: SponsorFactory
) : net.blueshell.api.integration.mapper.MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted sponsor`() {
            val sponsor = sponsorFactory.createBasic().apply {
                picture = persist(fileWithUploader(picture))
            }
            val saved = persist(sponsor)
            flushAndClear()

            val reloaded = reload(Sponsor::class.java, saved.id!!)
            val dto = sponsorMapper.toDTO(reloaded)

            assertThat(dto.id).isEqualTo(reloaded.id)
            assertThat(dto.name).isEqualTo(reloaded.name)
            assertThat(dto.description).isEqualTo(reloaded.description)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped sponsor`() {
            val sponsor = sponsorFactory.createBasic()
            val picture = persist(fileWithUploader(sponsor.picture))
            sponsor.picture = picture
            val dto = sponsorDTOFactory.createBasic()

            val mapped = sponsorMapper.fromDTO(dto, sponsor)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Sponsor::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.description).isEqualTo(dto.description)
            assertThat(reloaded.pictureId).isEqualTo(picture.id)
        }
    }
}
