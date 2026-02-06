package net.blueshell.api.mapper.user

import net.blueshell.api.common.enums.Role
import net.blueshell.api.factory.dto.user.AdvancedUserDTOFactory
import net.blueshell.api.factory.model.AddressFactory
import net.blueshell.api.mapper.MapperTestSupport
import net.blueshell.api.model.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.security.crypto.password.PasswordEncoder

@SpringBootTest
class AdvancedUserMapperIT @Autowired constructor(
    private val advancedUserMapper: AdvancedUserMapper,
    private val advancedUserDTOFactory: AdvancedUserDTOFactory,
    private val addressFactory: AddressFactory,
    private val passwordEncoder: PasswordEncoder
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

            assertThat(mapped.username).isEqualTo(dto.username)
            assertThat(mapped.email).isEqualTo(dto.email)
            assertThat(mapped.addressId).isEqualTo(address.id)

            val saved = persist(mapped)
            flushAndClear()

            val reloaded = reload(User::class.java, saved.id!!)

            assertThat(reloaded.username).isEqualTo(dto.username)
            assertThat(reloaded.email).isEqualTo(dto.email)
            assertThat(reloaded.addressId).isEqualTo(address.id)
        }

        @Test
        fun `creates user with restricted fields for member`() {
            authenticateAs(Role.MEMBER)
            val dto = advancedUserDTOFactory.createBasic().apply {
                initials = "TU"
                firstName = "Test"
                prefix = "van"
                lastName = "User"
                username = "newmember"
                email = "newmember@example.org"
                password = "Password123!"
            }

            val mapped = advancedUserMapper.fromDTO(dto, User())

            assertThat(mapped.username).isEqualTo(dto.username)
            assertThat(mapped.email).isEqualTo(dto.email)
            assertThat(mapped.firstName).isEqualTo(dto.firstName)
            assertThat(mapped.lastName).isEqualTo(dto.lastName)
            assertThat(passwordEncoder.matches(dto.password, mapped.password)).isTrue()
        }

        @Test
        fun `creates user with restricted fields but random password for admin`() {
            authenticateAs(Role.ADMIN)
            val dto = advancedUserDTOFactory.createBasic().apply {
                initials = "AD"
                firstName = "Admin"
                prefix = "de"
                lastName = "User"
                username = "newadmin"
                email = "newadmin@example.org"
                password = "AdminPassword123!"
            }

            val mapped = advancedUserMapper.fromDTO(dto, User())

            assertThat(mapped.username).isEqualTo(dto.username)
            assertThat(mapped.email).isEqualTo(dto.email)
            assertThat(mapped.firstName).isEqualTo(dto.firstName)
            assertThat(mapped.lastName).isEqualTo(dto.lastName)
            assertThat(passwordEncoder.matches(dto.password, mapped.password)).isFalse()
        }

        @Test
        fun `does not update restricted fields for member`() {
            authenticateAs(Role.MEMBER)
            val existing = persist(userFactory.createBasic().apply {
                setUsername("oldmember")
                email = "oldmember@example.org"
                firstName = "Old"
                lastName = "Member"
            })
            val dto = advancedUserDTOFactory.createBasic().apply {
                initials = "NM"
                firstName = "New"
                lastName = "Member"
                username = "newmember"
                email = "newmember@example.org"
                phoneNumber = "+31600000000"
            }

            val mapped = advancedUserMapper.fromDTO(dto, existing)

            assertThat(mapped.username).isEqualTo("oldmember")
            assertThat(mapped.email).isEqualTo("oldmember@example.org")
            assertThat(mapped.firstName).isEqualTo("Old")
            assertThat(mapped.lastName).isEqualTo("Member")
            assertThat(mapped.phoneNumber).isEqualTo(dto.phoneNumber)
        }

        @Test
        fun `updates restricted fields for admin`() {
            authenticateAs(Role.ADMIN)
            val existing = persist(userFactory.createBasic().apply {
                setUsername("oldadmin")
                email = "oldadmin@example.org"
                firstName = "Old"
                lastName = "Admin"
            })
            val dto = advancedUserDTOFactory.createBasic().apply {
                initials = "NA"
                firstName = "New"
                lastName = "Admin"
                username = "newadmin"
                email = "newadmin@example.org"
            }

            val mapped = advancedUserMapper.fromDTO(dto, existing)

            assertThat(mapped.username).isEqualTo(dto.username)
            assertThat(mapped.email).isEqualTo(dto.email)
            assertThat(mapped.firstName).isEqualTo(dto.firstName)
            assertThat(mapped.lastName).isEqualTo(dto.lastName)
        }
    }
}
