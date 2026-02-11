package net.blueshell.api.domain.event.web.dto

import net.blueshell.api.domain.event.application.EventService
import net.blueshell.api.domain.event.persistence.Event
import net.blueshell.api.domain.event.web.mapping.asEntity
import net.blueshell.api.domain.event.web.mapping.asSocialDto
import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.factory.dto.event.EventBannerDTOFactory
import net.blueshell.api.factory.dto.event.EventDTOFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.shared.enums.PlatformType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventDtoIT @Autowired constructor(
    private val eventDTOFactory: EventDTOFactory,
    private val eventBannerDTOFactory: EventBannerDTOFactory,
    private val fileDTOFactory: FileDTOFactory,
    private val fileFactory: FileFactory,
    private val eventService: EventService
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists banner and survey`() {
            authenticateAs(Role.BOARD)
            val committee = persistCommittee()
            val file = persist(fileWithUploader(fileFactory.createImage()))
            val dto = eventDTOFactory.createBasic().apply {
                committeeId = committee.id!!
                approved = true
                banner = eventBannerDTOFactory.createBasic().also {
                    it.file = fileDTOFactory.createBasic().also { fileDto -> fileDto.id = file.id }
                }
            }
            val event = eventFactory.createBasic()

            val mapped = dto.asEntity(event)
            mapped.banner?.file = file
            val saved = eventService.create(mapped)
            entityManager.flush()
            flushAndClear()

            val reloaded = reload(Event::class.java, saved.id!!)

            assertThat(reloaded.committeeId).isEqualTo(committee.id)
            assertThat(reloaded.banner).isNotNull
            assertThat(reloaded.banner!!.fileId).isEqualTo(file.id)
            assertThat(reloaded.approved).isTrue
        }

        @Test
        fun `maps platforms`() {
            val dto = eventDTOFactory.createBasic()
            val social = dto.asSocialDto()

            assertThat(social.text).isEqualTo(dto.description)
            assertThat(social.platforms).contains(
                PlatformType.FACEBOOK,
                PlatformType.TWITTER,
                PlatformType.INSTAGRAM
            )
        }
    }
}
