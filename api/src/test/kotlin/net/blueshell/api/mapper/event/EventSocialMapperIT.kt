package net.blueshell.api.mapper.event

import net.blueshell.api.common.enums.PlatformType
import net.blueshell.api.factory.dto.event.EventDTOFactory
import net.blueshell.api.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventSocialMapperIT @Autowired constructor(
    private val eventSocialMapper: EventSocialMapper,
    private val eventDTOFactory: EventDTOFactory
) : MapperTestSupport() {
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
