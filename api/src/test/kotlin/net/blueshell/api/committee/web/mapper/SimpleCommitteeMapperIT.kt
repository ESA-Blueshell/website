package net.blueshell.api.committee.web.mapper

import net.blueshell.api.factory.dto.committee.SimpleCommitteeDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.committee.web.mapper.SimpleCommitteeMapper
import net.blueshell.api.committee.persistence.Committee
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SimpleCommitteeMapperIT @Autowired constructor(
    private val simpleCommitteeMapper: SimpleCommitteeMapper
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
}
