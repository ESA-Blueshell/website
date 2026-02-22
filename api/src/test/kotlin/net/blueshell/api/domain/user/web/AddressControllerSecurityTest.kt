package net.blueshell.api.domain.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Security tests for AddressController.
 *
 * Verifies authorization rules are correctly enforced per ADR-014:
 * - Users can create addresses for themselves
 * - BOARD can create addresses for any user
 * - Users can update their own address
 * - BOARD can list all addresses
 * - Users can read their own address
 * - BOARD can delete any address
 */
@SpringBootTest
class AddressControllerSecurityTest : UserTestSupport() {

    @Nested
    inner class CreateAddress {

        @Test
        fun `allows user to create address for self`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/addresses")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${user.id},"street":"Main St","houseNumber":"123","zipCode":"1234AB","city":"Amsterdam","country":"NL"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `allows BOARD to create address for any user`() {
            val board = createUserWithRole(Role.BOARD)
            val targetUser = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/addresses")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${targetUser.id},"street":"Main St","houseNumber":"123","zipCode":"1234AB","city":"Amsterdam","country":"NL"}""")
            )
                .andExpect(status().isCreated)
        }

        @Test
        fun `denies user from creating address for other user`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/addresses")
                    .with(bearer(user1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${user2.id},"street":"Main St","houseNumber":"123","zipCode":"1234AB","city":"Amsterdam","country":"NL"}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val userId = createUserWithRole(Role.MEMBER).id!!

            mvc.perform(
                post("/addresses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":$userId,"street":"Main St","houseNumber":"123","zipCode":"1234AB","city":"Amsterdam","country":"NL"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class UpdateAddress {

        @Test
        fun `allows user to update own address`() {
            val user = assignAddress(createUserWithRole(Role.MEMBER))
            val address = refreshUser(user).address!!
            val addressId = address.id!!

            mvc.perform(
                put("/addresses/{id}", addressId)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"street":"Updated St","houseNumber":"456","zipCode":"5678CD","city":"Utrecht","country":"NL","version":${address.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to update any address`() {
            val board = createUserWithRole(Role.BOARD)
            val address = createAddressFixture()
            val addressId = address.id!!

            mvc.perform(
                put("/addresses/{id}", addressId)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"street":"Updated St","houseNumber":"456","zipCode":"5678CD","city":"Utrecht","country":"NL","version":${address.version}}""")
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from updating other user's address`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val address = refreshUser(assignAddress(user2)).address!!
            val addressId = address.id!!

            mvc.perform(
                put("/addresses/{id}", addressId)
                    .with(bearer(user1))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"street":"Hacked","houseNumber":"999","zipCode":"9999XX","city":"Hacked","country":"NL","version":${address.version}}""")
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val address = createAddressFixture()
            val addressId = address.id!!

            mvc.perform(
                put("/addresses/{id}", addressId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"street":"Unauthorized","houseNumber":"000","zipCode":"0000XX","city":"Unauthorized","country":"NL","version":${address.version}}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindAllAddresses {

        @Test
        fun `allows BOARD to list all addresses`() {
            val board = createUserWithRole(Role.BOARD)

            mvc.perform(
                get("/addresses")
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies non-BOARD users from listing all addresses`() {
            val member = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/addresses")
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            mvc.perform(get("/addresses"))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class FindAddressById {

        @Test
        fun `allows user to read own address`() {
            val user = assignAddress(createUserWithRole(Role.MEMBER))
            val addressId = refreshUser(user).addressId!!

            mvc.perform(
                get("/addresses/{id}", addressId)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `allows BOARD to read any address`() {
            val board = createUserWithRole(Role.BOARD)
            val addressId = createAddressFixture().id!!

            mvc.perform(
                get("/addresses/{id}", addressId)
                    .with(bearer(board))
            )
                .andExpect(status().isOk)
        }

        @Test
        fun `denies user from reading other user's address`() {
            val user1 = createUserWithRole(Role.MEMBER)
            val user2 = createUserWithRole(Role.MEMBER)
            val addressId = assignAddress(user2).addressId!!

            mvc.perform(
                get("/addresses/{id}", addressId)
                    .with(bearer(user1))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val addressId = createAddressFixture().id!!

            mvc.perform(get("/addresses/{id}", addressId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteUserAddress {

        @Test
        fun `allows BOARD to delete user address`() {
            val board = createUserWithRole(Role.BOARD)
            val addressId = createAddressFixture().id!!

            mvc.perform(
                delete("/addresses/{id}", addressId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting user address`() {
            val member = createUserWithRole(Role.MEMBER)
            val addressId = createAddressFixture().id!!

            mvc.perform(
                delete("/addresses/{id}", addressId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val addressId = createAddressFixture().id!!

            mvc.perform(delete("/addresses/{id}", addressId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class DeleteAddressById {

        @Test
        fun `allows BOARD to delete address by id`() {
            val board = createUserWithRole(Role.BOARD)
            val addressId = createAddressFixture().id!!

            mvc.perform(
                delete("/addresses/{id}", addressId)
                    .with(bearer(board))
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun `denies non-BOARD users from deleting addresses`() {
            val member = createUserWithRole(Role.MEMBER)
            val addressId = createAddressFixture().id!!

            mvc.perform(
                delete("/addresses/{id}", addressId)
                    .with(bearer(member))
            )
                .andExpect(status().isForbidden)
        }

        @Test
        fun `returns 401 when unauthenticated`() {
            val addressId = createAddressFixture().id!!

            mvc.perform(delete("/addresses/{id}", addressId))
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    inner class RoleHierarchy {

        @Test
        fun `ADMIN can perform BOARD operations`() {
            val admin = createUserWithRole(Role.ADMIN)

            mvc.perform(
                get("/addresses")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)
        }
    }
}
