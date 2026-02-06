package net.blueshell.api.mapper.event

import net.blueshell.api.factory.dto.GuestDTOFactory
import net.blueshell.api.factory.model.GuestFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.event.Guest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GuestMapperIT @Autowired constructor(
    private val guestMapper: GuestMapper,
    private val guestDTOFactory: GuestDTOFactory,
    private val guestFactory: GuestFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted guest`() {
            val guest = persist(guestFactory.createBasic())

            val dto = guestMapper.toDTO(guest)

            assertThat(dto.id).isEqualTo(guest.id)
            assertThat(dto.name).isEqualTo(guest.name)
            assertThat(dto.email).isEqualTo(guest.email)
            assertThat(dto.accessToken).isEqualTo(guest.accessToken)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists generated access token`() {
            val dto = guestDTOFactory.createBasic().apply {
                accessToken = null
            }
            val guest = guestFactory.createBasic()

            guestMapper.fromDTO(dto, guest)
            val saved = persist(guest)
            flushAndClear()

            val reloaded = reload(Guest::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.accessToken).isNotBlank
        }
    }
}
