package net.blueshell.api.domain.user.web

import net.blueshell.api.domain.user.persistence.repository.AddressRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class AddressControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var addressRepository: AddressRepository

    private fun createPayload(userId: Long): String =
        """{"userId":$userId,"country":"NL","city":"Enschede","street":"Noorderhagen","houseNumber":"14","zipCode":"7511EL"}"""

    private fun updatePayload(version: Long): String =
        """{"country":"NL","city":"Utrecht","street":"Nieuwegracht","houseNumber":"22A","zipCode":"3512LS","version":$version}"""

    @Nested
    inner class CreateAddress {

        @Test
        fun `creates address`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/addresses")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(user.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNumber)
                .andExpect(jsonPath("$.country").value("NL"))
                .andExpect(jsonPath("$.city").value("Enschede"))
                .andExpect(jsonPath("$.street").value("Noorderhagen"))
                .andExpect(jsonPath("$.houseNumber").value("14"))
                .andExpect(jsonPath("$.zipCode").value("7511EL"))

            val refreshed = refreshUser(user)
            assertThat(refreshed.address).isNotNull
            assertThat(refreshed.address!!.city).isEqualTo("Enschede")
        }

        @Test
        fun `returns bad request for invalid create payload`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/addresses")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"userId":${user.id},"country":"NL","city":"","street":"Noorderhagen","houseNumber":"14","zipCode":"7511EL"}"""
                    )
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun `returns not found when user does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                post("/addresses")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(999999L))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class UpdateAddress {

        @Test
        fun `updates address`() {
            val user = assignAddress(createUserWithRole(Role.MEMBER))
            val address = refreshUser(user).address!!

            mvc.perform(
                put("/addresses/{id}", address.id)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(address.version))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(address.id))
                .andExpect(jsonPath("$.city").value("Utrecht"))
                .andExpect(jsonPath("$.street").value("Nieuwegracht"))
                .andExpect(jsonPath("$.houseNumber").value("22A"))
                .andExpect(jsonPath("$.zipCode").value("3512LS"))

            val updated = refreshUser(user).address!!
            assertThat(updated.city).isEqualTo("Utrecht")
            assertThat(updated.street).isEqualTo("Nieuwegracht")
        }

        @Test
        fun `returns not found when address does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                put("/addresses/{id}", 999999L)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(0))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindAllAddresses {

        @Test
        fun `lists addresses`() {
            val board = createUserWithRole(Role.BOARD)
            createAddressFixture()

            mvc.perform(
                get("/addresses")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$").isArray)
                .andExpect(jsonPath("$[0].id").isNumber)
        }
    }

    @Nested
    inner class FindAddressById {

        @Test
        fun `finds address by id`() {
            val user = assignAddress(createUserWithRole(Role.MEMBER))
            val address = refreshUser(user).address!!

            mvc.perform(
                get("/addresses/{id}", address.id)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(address.id))
                .andExpect(jsonPath("$.city").value(address.city))
                .andExpect(jsonPath("$.street").value(address.street))
        }

        @Test
        fun `returns not found when address does not exist`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/addresses/{id}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class DeleteAddressById {

        @Test
        fun `deletes address by id`() {
            val board = createUserWithRole(Role.BOARD)
            val address = createAddressFixture()

            mvc.perform(
                delete("/addresses/{id}", address.id)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)

            assertThat(addressRepository.existsById(address.id!!)).isFalse()
        }

        @Test
        fun `returns not found when deleting missing address`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                delete("/addresses/{id}", 999999L)
                    .with(bearer(board))
            )
                .andExpect(status().isNotFound)
        }
    }
}
