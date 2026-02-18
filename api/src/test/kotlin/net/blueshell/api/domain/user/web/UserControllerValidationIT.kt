package net.blueshell.api.domain.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.hamcrest.Matchers.hasItem
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
class UserControllerValidationIT : UserTestSupport() {

    private fun createPayload(
        username: String,
        email: String,
        discord: String,
        phoneNumber: String,
    ): String =
        """{"username":"$username","initials":"VU","firstName":"Validation","lastName":"User","newsletter":true,"password":"Password123!","email":"$email","discord":"$discord","phoneNumber":"$phoneNumber"}"""

    private fun updatePayload(discord: String, phoneNumber: String, version: Long): String =
        """{"kind":"user","discord":"$discord","phoneNumber":"$phoneNumber","newsletter":true,"version":$version}"""

    @Nested
    inner class CreateUserUniqueness {

        @Test
        fun `duplicate username returns field validation error`() {
            val existing = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createPayload(
                            username = existing.username,
                            email = "new_${System.currentTimeMillis()}@example.com",
                            discord = "newdiscord${System.currentTimeMillis()}",
                            phoneNumber = "+3161111${System.currentTimeMillis().toString().takeLast(4)}"
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("username")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("Username is taken.")))
        }

        @Test
        fun `duplicate email returns field validation error`() {
            val existing = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createPayload(
                            username = "new_${System.currentTimeMillis()}",
                            email = existing.email,
                            discord = "newdiscord${System.currentTimeMillis()}",
                            phoneNumber = "+3162222${System.currentTimeMillis().toString().takeLast(4)}"
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("Email is taken.")))
        }

        @Test
        fun `duplicate discord returns field validation error`() {
            val existing = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createPayload(
                            username = "new_${System.currentTimeMillis()}",
                            email = "new_${System.currentTimeMillis()}@example.com",
                            discord = existing.discord,
                            phoneNumber = "+3163333${System.currentTimeMillis().toString().takeLast(4)}"
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("discord")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("Discord is taken.")))
        }

        @Test
        fun `duplicate phone number returns field validation error`() {
            val existing = createUserWithRole(Role.GUEST)

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        createPayload(
                            username = "new_${System.currentTimeMillis()}",
                            email = "new_${System.currentTimeMillis()}@example.com",
                            discord = "newdiscord${System.currentTimeMillis()}",
                            phoneNumber = existing.phoneNumber
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phoneNumber")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("Phone number is taken.")))
        }
    }

    @Nested
    inner class UpdateUserUniqueness {

        @Test
        fun `duplicate discord on update returns field validation error`() {
            val primary = createUserWithRole(Role.GUEST)
            val conflicting = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/{id}", primary.id)
                    .with(bearer(primary))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        updatePayload(
                            discord = conflicting.discord,
                            phoneNumber = primary.phoneNumber,
                            version = primary.version
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("discord")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("Discord is taken.")))
        }

        @Test
        fun `duplicate phone number on update returns field validation error`() {
            val primary = createUserWithRole(Role.GUEST)
            val conflicting = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/{id}", primary.id)
                    .with(bearer(primary))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        updatePayload(
                            discord = primary.discord,
                            phoneNumber = conflicting.phoneNumber,
                            version = primary.version
                        )
                    )
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phoneNumber")))
                .andExpect(jsonPath("$.errors[*].message").value(hasItem("Phone number is taken.")))
        }
    }
}
