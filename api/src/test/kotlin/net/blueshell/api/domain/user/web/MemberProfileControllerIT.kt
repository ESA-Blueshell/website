package net.blueshell.api.domain.user.web

import net.blueshell.api.domain.user.persistence.repository.MemberProfileRepository
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
class MemberProfileControllerIT : UserTestSupport() {

    @Autowired
    private lateinit var memberProfileRepository: MemberProfileRepository

    private fun createPayload(userId: Long): String =
        """{"userId":$userId,"dateOfBirth":"1999-04-12","studentNumber":"s1234567","gender":"X","photoConsent":true,"nationality":"NL","bhv":true,"ehbo":false}"""

    private fun updatePayload(version: Long): String =
        """{"dateOfBirth":"2000-01-01","studentNumber":"s7654321","gender":"F","photoConsent":false,"nationality":"DE","bhv":false,"ehbo":true,"version":$version}"""

    @Nested
    inner class CreateMemberProfile {
        @Test
        fun `creates member profile for authenticated user`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberProfiles")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(user.id!!))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.studentNumber").value("s1234567"))
                .andExpect(jsonPath("$.gender").value("X"))
                .andExpect(jsonPath("$.photoConsent").value(true))
                .andExpect(jsonPath("$.nationality").value("NL"))
                .andExpect(jsonPath("$.bhv").value(true))
                .andExpect(jsonPath("$.ehbo").value(false))

            val profile = memberProfileRepository.findById(user.id!!).orElseThrow()
            assertThat(profile.userId).isEqualTo(user.id)
            assertThat(profile.studentNumber).isEqualTo("s1234567")
            assertThat(profile.gender).isEqualTo("X")
            assertThat(profile.photoConsent).isTrue()
            assertThat(profile.nationality).isEqualTo("NL")
        }

        @Test
        fun `returns conflict when creating duplicate member profile`() {
            val user = assignMemberProfile(createUserWithRole(Role.MEMBER))

            mvc.perform(
                post("/memberProfiles")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(createPayload(user.id!!))
            )
                .andExpect(status().isConflict)
        }

        @Test
        fun `returns bad request for invalid create payload`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                post("/memberProfiles")
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"userId":${user.id},"dateOfBirth":"1999-04-12","studentNumber":"","gender":"X","photoConsent":true,"nationality":"NL","bhv":true,"ehbo":false}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    inner class UpdateMemberProfile {
        @Test
        fun `updates existing member profile`() {
            val user = assignMemberProfile(createUserWithRole(Role.MEMBER))
            val profile = refreshUser(user).memberProfile!!

            mvc.perform(
                put("/users/{userId}/memberProfiles", user.id)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(profile.version))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.studentNumber").value("s7654321"))
                .andExpect(jsonPath("$.gender").value("F"))
                .andExpect(jsonPath("$.photoConsent").value(false))
                .andExpect(jsonPath("$.nationality").value("DE"))
                .andExpect(jsonPath("$.bhv").value(false))
                .andExpect(jsonPath("$.ehbo").value(true))

            val updated = memberProfileRepository.findById(user.id!!).orElseThrow()
            assertThat(updated.studentNumber).isEqualTo("s7654321")
            assertThat(updated.gender).isEqualTo("F")
            assertThat(updated.photoConsent).isFalse()
            assertThat(updated.nationality).isEqualTo("DE")
            assertThat(updated.bhv).isFalse()
            assertThat(updated.ehbo).isTrue()
        }

        @Test
        fun `returns not found when updating missing member profile`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                put("/users/{userId}/memberProfiles", user.id)
                    .with(bearer(user))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(updatePayload(0))
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    inner class FindMemberProfileByUserId {
        @Test
        fun `finds member profile by user id`() {
            val user = assignMemberProfile(createUserWithRole(Role.MEMBER))
            val profile = refreshUser(user).memberProfile!!

            mvc.perform(
                get("/users/{userId}/memberProfiles", user.id)
                    .with(bearer(user))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(profile.id))
                .andExpect(jsonPath("$.userId").value(user.id))
                .andExpect(jsonPath("$.studentNumber").value(profile.studentNumber))
        }

        @Test
        fun `returns not found when member profile does not exist`() {
            val user = createUserWithRole(Role.MEMBER)

            mvc.perform(
                get("/users/{userId}/memberProfiles", user.id)
                    .with(bearer(user))
            )
                .andExpect(status().isNotFound)
        }
    }
}
