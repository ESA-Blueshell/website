package net.blueshell.api.user.web.dto

import net.blueshell.api.factory.dto.user.SimpleUserDTOFactory
import net.blueshell.api.shared.mapper.MapperTestSupport
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.web.mapping.asEntity
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder

@SpringBootTest
class SimpleUserDtoIT @Autowired constructor(
    private val simpleUserDTOFactory: SimpleUserDTOFactory,
    private val userService: UserService,
    private val passwordEncoder: PasswordEncoder
) : MapperTestSupport() {
    @Nested
    inner class AsEntity {
        @Test
        fun `persists new user`() {
            val dto = simpleUserDTOFactory.createBasic()
            val user = userFactory.createBasic()

            val mapped = dto.asEntity(user, passwordEncoder)
            val saved = userService.create(mapped)
            flushAndClear()

            val reloaded = reload(User::class.java, saved.id!!)

            assertThat(reloaded.username).isEqualTo(dto.username)
            assertThat(reloaded.email).isEqualTo(dto.email)
            assertThat(reloaded.password).isNotEqualTo(dto.password)
        }
    }
}
