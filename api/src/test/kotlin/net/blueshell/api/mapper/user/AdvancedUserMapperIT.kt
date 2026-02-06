package net.blueshell.api.mapper.user

import net.blueshell.api.factory.dto.user.AdvancedUserDTOFactory
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AdvancedUserMapperIT @Autowired constructor(
    private val advancedUserMapper: AdvancedUserMapper,
    private val advancedUserDTOFactory: AdvancedUserDTOFactory,
    private val addressFactory: AddressFactory
) : MapperTestSupport() {
    @Nested
    inner class ToDTO {
        @Test
        fun `maps persisted user`() {
            val user = persist(userFactory.createFull())

            val dto = advancedUserMapper.toDTO(user)

            assertThat(dto.id).isEqualTo(user.id)
            assertThat(dto.username).isEqualTo(user.username)
            assertThat(dto.email).isEqualTo(user.email)
            assertThat(dto.fullName).isEqualTo(user.fullName)
            assertThat(dto.roles).containsAll(user.inheritedRoles)
        }
    }

    @Nested
    inner class FromDTO {
        @Test
        fun `persists profile fields`() {
            val address = persist(addressFactory.createBasic())
            val dto = advancedUserDTOFactory.createBasic().apply {
                addressId = address.id
            }
            val user = userFactory.createBasic()

            val mapped = advancedUserMapper.fromDTO(dto, user)
            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(User::class.java, saved.id!!)

            assertThat(reloaded.username).isEqualTo(dto.username)
            assertThat(reloaded.email).isEqualTo(dto.email)
            assertThat(reloaded.addressId).isEqualTo(address.id)
        }
    }
}
