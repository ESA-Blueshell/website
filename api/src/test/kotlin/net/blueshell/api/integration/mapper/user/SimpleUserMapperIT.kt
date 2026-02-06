package net.blueshell.api.integration.mapper.user

import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import net.blueshell.api.integration.mapper.MapperTestSupport
import net.blueshell.api.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class SimpleUserMapperIT @Autowired constructor(
    private val simpleUserMapper: SimpleUserMapper,
    private val simpleUserDTOFactory: SimpleUserDTOFactory
) : net.blueshell.api.integration.mapper.MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted user`() {
            val user = persist(userFactory.createBasic())

            val dto = simpleUserMapper.toDTO(user)

            assertThat(dto.id).isEqualTo(user.id)
            assertThat(dto.username).isEqualTo(user.username)
            assertThat(dto.email).isEqualTo(user.email)
            assertThat(dto.fullName).isEqualTo(user.fullName)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists new user`() {
            val dto = simpleUserDTOFactory.createBasic()
            val user = userFactory.createBasic()

            val mapped = simpleUserMapper.fromDTO(dto, user)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(User::class.java, saved.id!!)

            assertThat(reloaded.username).isEqualTo(dto.username)
            assertThat(reloaded.email).isEqualTo(dto.email)
            assertThat(reloaded.password).isNotEqualTo(dto.password)
        }
    }
}
