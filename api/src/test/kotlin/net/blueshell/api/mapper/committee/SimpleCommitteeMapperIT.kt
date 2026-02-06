package net.blueshell.api.mapper.committee

import net.blueshell.api.mapper.MapperTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SimpleCommitteeMapperIT @Autowired constructor(
    private val simpleCommitteeMapper: SimpleCommitteeMapper
) : MapperTestSupport() {
    @Test
    fun `maps core fields`() {
        val committee = persistCommittee()
        val dto = simpleCommitteeMapper.toDTO(committee)

        assertThat(dto.name).isEqualTo(committee.name)
        assertThat(dto.description).isEqualTo(committee.description)
    }
}
