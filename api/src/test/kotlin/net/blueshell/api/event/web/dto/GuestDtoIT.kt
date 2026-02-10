package net.blueshell.api.event.web.dto

import net.blueshell.api.event.application.GuestService
import net.blueshell.api.event.persistence.Guest
import net.blueshell.api.event.web.mapping.asEntity
import net.blueshell.api.factory.dto.GuestDTOFactory
import net.blueshell.api.factory.model.event.GuestFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class GuestDtoIT @Autowired constructor(
    private val guestDTOFactory: GuestDTOFactory,
    private val guestFactory: GuestFactory,
    private val guestService: GuestService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists generated access token`() {
            val dto = guestDTOFactory.createBasic()
            val guest = guestFactory.createBasic()

            dto.asEntity(guest)
            val saved = guestService.create(guest)
            flushAndClear()

            val reloaded = reload(Guest::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.accessToken).isNotBlank
        }
    }
}
