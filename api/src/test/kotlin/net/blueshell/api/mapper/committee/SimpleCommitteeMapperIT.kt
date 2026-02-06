package net.blueshell.api.mapper.committee

import net.blueshell.api.factory.dto.committee.SimpleCommitteeDTOFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.committee.Committee
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SimpleCommitteeMapperIT @Autowired constructor(
    private val simpleCommitteeMapper: SimpleCommitteeMapper,
    private val simpleCommitteeDTOFactory: SimpleCommitteeDTOFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps core fields`() {
            val committee = persistCommittee()
            val dto = simpleCommitteeMapper.toDTO(committee)

            assertThat(dto.name).isEqualTo(committee.name)
            assertThat(dto.description).isEqualTo(committee.description)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists mapped committee`() {
            val dto = simpleCommitteeDTOFactory.createBasic()
            val committee = committeeFactory.createBasic()

            val mapped = simpleCommitteeMapper.fromDTO(dto, committee)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(Committee::class.java, saved.id!!)

            assertThat(reloaded.name).isEqualTo(dto.name)
            assertThat(reloaded.description).isEqualTo(dto.description)
        }
    }
}
