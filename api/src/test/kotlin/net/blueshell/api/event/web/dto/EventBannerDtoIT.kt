package net.blueshell.api.event.web.dto

import net.blueshell.api.event.application.EventBannerService
import net.blueshell.api.event.persistence.EventBanner
import net.blueshell.api.event.web.mapping.asEntity
import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.factory.dto.event.EventBannerDTOFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.factory.model.event.EventBannerFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventBannerDtoIT @Autowired constructor(
    private val eventBannerDTOFactory: EventBannerDTOFactory,
    private val fileDTOFactory: FileDTOFactory,
    private val eventBannerFactory: EventBannerFactory,
    private val fileFactory: FileFactory,
    private val eventBannerService: EventBannerService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
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

            val mapped = dto.asEntity(banner)
            mapped.event = event
            mapped.file = file

            val saved = eventBannerService.create(mapped)
            flushAndClear()

            val reloaded = reload(EventBanner::class.java, saved.id)

            assertThat(reloaded.eventId).isEqualTo(event.id)
            assertThat(reloaded.fileId).isEqualTo(file.id)
        }
    }
}
