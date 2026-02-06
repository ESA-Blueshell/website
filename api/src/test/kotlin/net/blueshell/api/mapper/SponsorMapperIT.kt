package net.blueshell.api.mapper

import net.blueshell.api.factory.dto.SponsorDTOFactory
import net.blueshell.api.factory.model.SponsorFactory
import net.blueshell.api.model.Sponsor
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SponsorMapperIT @Autowired constructor(
    private val sponsorMapper: SponsorMapper,
    private val sponsorDTOFactory: SponsorDTOFactory,
    private val sponsorFactory: SponsorFactory
) : MapperTestSupport() {
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
        val mappedDto = sponsorMapper.toDTO(reloaded)

        assertThat(reloaded.name).isEqualTo(dto.name)
        assertThat(reloaded.description).isEqualTo(dto.description)
        assertThat(reloaded.pictureId).isEqualTo(picture.id)
        assertThat(mappedDto.name).isEqualTo(reloaded.name)
    }
}
