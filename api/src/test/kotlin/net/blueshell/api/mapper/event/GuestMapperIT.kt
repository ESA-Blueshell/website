package net.blueshell.api.mapper.event

import net.blueshell.api.factory.dto.GuestDTOFactory
import net.blueshell.api.factory.model.GuestFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.event.Guest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GuestMapperIT @Autowired constructor(
    private val guestMapper: GuestMapper,
    private val guestDTOFactory: GuestDTOFactory,
    private val guestFactory: GuestFactory
) : MapperTestSupport() {
    @Test
    fun `persists generated access token`() {
        val dto = guestDTOFactory.createBasic()
        val guest = guestFactory.createBasic()

        guestMapper.fromDTO(dto, guest)
        val saved = persist(guest)
        flushAndClear()

        val reloaded = reload(Guest::class.java, saved.id!!)
        val mappedDto = guestMapper.toDTO(reloaded)

        assertThat(reloaded.name).isEqualTo(dto.name)
        assertThat(reloaded.accessToken).isNotBlank
        assertThat(mappedDto.accessToken).isNotBlank
    }
}
