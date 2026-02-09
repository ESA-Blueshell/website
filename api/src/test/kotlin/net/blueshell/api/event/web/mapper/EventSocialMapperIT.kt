package net.blueshell.api.event.web.mapper

import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.factory.dto.event.EventDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.event.web.mapper.EventSocialMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventSocialMapperIT @Autowired constructor(
    private val eventSocialMapper: EventSocialMapper,
    private val eventDTOFactory: EventDTOFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps platforms`() {
            val dto = eventDTOFactory.createBasic()
            val social = eventSocialMapper.toSocialDto(dto)

            assertThat(social.text).isEqualTo(dto.description)
            assertThat(social.platforms).contains(
                PlatformType.FACEBOOK,
                PlatformType.TWITTER,
                PlatformType.INSTAGRAM
            )
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `is not supported`() {
            val hasFromDto = EventSocialMapper::class.java.methods.any { it.name == "fromDTO" }

            assertThat(hasFromDto).isFalse
        }
    }
}
