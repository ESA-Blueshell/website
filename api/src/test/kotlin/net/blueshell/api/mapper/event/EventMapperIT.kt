package net.blueshell.api.mapper.event

import net.blueshell.api.common.enums.Role
import net.blueshell.api.factory.dto.FileDTOFactory
import net.blueshell.api.factory.dto.event.EventBannerDTOFactory
import net.blueshell.api.factory.dto.event.EventDTOFactory
import net.blueshell.api.factory.model.EventFactory
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.event.Event
import org.assertj.core.api.Assertions.assertThat
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
        mapped.signUpForm?.questions?.forEach { it.survey = mapped.signUpForm!! }

        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(Event::class.java, saved.id!!)
        val mappedDto = eventMapper.toDTO(reloaded)

        assertThat(reloaded.committeeId).isEqualTo(committee.id)
        assertThat(reloaded.banner).isNotNull
        assertThat(reloaded.banner!!.fileId).isEqualTo(file.id)
        assertThat(reloaded.approved).isTrue
        assertThat(mappedDto.id).isEqualTo(saved.id)
    }
}
