package net.blueshell.api.mapper.user

import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SimpleUserMapperIT @Autowired constructor(
    private val simpleUserMapper: SimpleUserMapper,
    private val simpleUserDTOFactory: SimpleUserDTOFactory
) : MapperTestSupport() {
    @Test
    fun `persists new user`() {
        val dto = simpleUserDTOFactory.createBasic()
        val user = userFactory.createBasic()

        val mapped = simpleUserMapper.fromDTO(dto, user)
        val saved = persist(mapped)
        flushAndClear()

        val reloaded = reload(User::class.java, saved.id!!)
        val mappedDto = simpleUserMapper.toDTO(reloaded)

        assertThat(reloaded.username).isEqualTo(dto.username)
        assertThat(reloaded.email).isEqualTo(dto.email)
        assertThat(reloaded.password).isNotEqualTo(dto.password)
        assertThat(mappedDto.id).isEqualTo(reloaded.id)
    }
}
