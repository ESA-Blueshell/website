package net.blueshell.api.feature.event.mapper

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.factory.dto.event.EventBannerDTOFactory
import net.blueshell.api.factory.dto.event.EventDTOFactory
import net.blueshell.api.factory.model.event.EventFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.feature.shared.mapper.MapperTestSupport
import net.blueshell.api.feature.event.mapper.EventMapper
import net.blueshell.api.feature.event.model.Event
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class EventMapperIT @Autowired constructor(
    private val eventMapper: EventMapper,
    private val eventDTOFactory: EventDTOFactory,
    private val eventBannerDTOFactory: EventBannerDTOFactory,
    private val fileDTOFactory: FileDTOFactory,
    private val fileFactory: FileFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted event`() {
            val committee = persistCommittee()
            val event = persist(eventFactory.createBasic().apply {
                this.committee = committee
                this.committeeId = committee.id!!
            })

            val dto = eventMapper.toDTO(event)

            assertThat(dto.id).isEqualTo(event.id)
            assertThat(dto.committeeId).isEqualTo(event.committeeId)
            assertThat(dto.title).isEqualTo(event.title)
            assertThat(dto.description).isEqualTo(event.description)
            assertThat(dto.approved).isEqualTo(event.approved)
        }
    }

    @Nested
    inner class FromDTO {
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

            val mapped = eventMapper.fromDTO(dto, event)
            mapped.banner?.file = file
            val saved = persist(mapped)
            entityManager.flush()

            flushAndClear()

            val reloaded = reload(Event::class.java, saved.id!!)

            assertThat(reloaded.committeeId).isEqualTo(committee.id)
            assertThat(reloaded.banner).isNotNull
            assertThat(reloaded.banner!!.fileId).isEqualTo(file.id)
            assertThat(reloaded.approved).isTrue
        }
    }
}
