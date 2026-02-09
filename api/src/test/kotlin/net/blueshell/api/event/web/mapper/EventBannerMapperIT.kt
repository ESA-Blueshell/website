package net.blueshell.api.event.web.mapper

import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.factory.dto.event.EventBannerDTOFactory
import net.blueshell.api.factory.model.event.EventBannerFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.event.web.mapper.EventBannerMapper
import net.blueshell.api.event.persistence.EventBanner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventBannerMapperIT @Autowired constructor(
    private val eventBannerMapper: EventBannerMapper,
    private val eventBannerDTOFactory: EventBannerDTOFactory,
    private val fileDTOFactory: FileDTOFactory,
    private val eventBannerFactory: EventBannerFactory,
    private val fileFactory: FileFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted banner`() {
            val event = persistEvent()
            val file = persist(fileWithUploader(fileFactory.createImage()))
            val banner = persist(eventBannerFactory.createBasic().apply {
                this.event = event
                this.file = file
            })

            val dto = eventBannerMapper.toDTO(banner)

            assertThat(dto.file?.id).isEqualTo(file.id)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped banner`() {
            val event = persistEvent()
            val file = persist(fileWithUploader(fileFactory.createImage()))
            val dto = eventBannerDTOFactory.createBasic().apply {
                this.file = fileDTOFactory.createBasic().also { it.id = file.id }
            }
            val banner = eventBannerFactory.createBasic().apply {
                this.event = event
                this.file = file
            }

            val mapped = eventBannerMapper.fromDTO(dto, banner)
            mapped.event = event
            mapped.file = file

            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(EventBanner::class.java, saved.id)

            assertThat(reloaded.eventId).isEqualTo(event.id)
            assertThat(reloaded.fileId).isEqualTo(file.id)
        }
    }
}
