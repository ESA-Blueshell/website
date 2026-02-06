package net.blueshell.api.mapper.user

import net.blueshell.api.factory.dto.user.AdvancedUserDTOFactory
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest
class AdvancedUserMapperIT @Autowired constructor(
    private val advancedUserMapper: AdvancedUserMapper,
    private val advancedUserDTOFactory: AdvancedUserDTOFactory,
    private val addressFactory: AddressFactory
) : MapperTestSupport() {
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
        val mappedDto = advancedUserMapper.toDTO(reloaded)

        assertThat(reloaded.username).isEqualTo(dto.username)
        assertThat(reloaded.email).isEqualTo(dto.email)
        assertThat(reloaded.addressId).isEqualTo(address.id)
        assertThat(mappedDto.addressId).isEqualTo(address.id)
    }
}
