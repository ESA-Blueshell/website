package net.blueshell.api.domain.user.web

import net.blueshell.api.factory.user.web.request.UserRequestFactory
import net.blueshell.api.domain.user.persistence.repository.MemberProfileRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.job.ContactJobs
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
class UserControllerIT : UserTestSupport() {
    @Autowired
    private lateinit var memberProfileRepository: MemberProfileRepository

    @Autowired
    private lateinit var userRequestFactory: UserRequestFactory

    @Nested
    inner class CreateUser {
        @Test
        fun `creates guest user publicly`() {
            val guestUsername = "guest_it_${System.currentTimeMillis()}"
            val guestEmail = "$guestUsername@example.com"

            mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(userRequestFactory.createUserPayload(guestUsername, guestEmail))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.id").isNotEmpty)
                .andExpect(jsonPath("$.username").value(guestUsername))
                .andExpect(jsonPath("$.email").value(guestEmail))

            val persistedUser = userRepository.findByUsername(guestUsername).orElseThrow()
            val jobs = findJobsByType(ContactJobs.SyncContact.type)
            assertThat(jobs)
                .describedAs("Should schedule contact sync job on user creation")
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.payload).contains("\"userId\":${persistedUser.id}")
                }
        }

        @Test
        fun `creates user with member profile when provided`() {
            val username = "guest_with_profile_${System.currentTimeMillis()}"
            val email = "$username@example.com"
            val result = mvc.perform(
                post("/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"username":"$username","initials":"GU","firstName":"Guest","lastName":"User","newsletter":true,"password":"Password123!","email":"$email","discord":"guest#1234","phoneNumber":"+31612345678","memberProfile":{"dateOfBirth":"1999-04-12","studentNumber":"s1234567","gender":"X","photoConsent":true,"nationality":"NL","bhv":true,"ehbo":false}}"""
                    )
            )
                .andExpect(status().isCreated)
                .andReturn()

            val userId = mapper.readTree(result.response.contentAsByteArray).path("id").asLong()
            val profile = memberProfileRepository.findById(userId).orElseThrow()

            assertThat(profile.userId).isEqualTo(userId)
            assertThat(profile.studentNumber).isEqualTo("s1234567")
            assertThat(profile.gender).isEqualTo("X")
            assertThat(profile.photoConsent).isTrue()
            assertThat(profile.nationality).isEqualTo("NL")
            assertThat(profile.bhv).isTrue()
            assertThat(profile.ehbo).isFalse()
            assertThat(profile.dateOfBirth.toLocalDate().toString()).isEqualTo("1999-04-12")
        }

        @Test
        fun `board can create user without providing password`() {
            val board = createUserWithRole(Role.BOARD)
            val username = "board_created_${System.currentTimeMillis()}"
            val payload =
                """{"username":"$username","initials":"BC","firstName":"Board","lastName":"Created","newsletter":true,"email":"$username@example.com","discord":"boardcreated#1234","phoneNumber":"+31612345000"}"""

            mvc.perform(
                post("/users")
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(payload)
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.username").value(username))

            val persistedUser = userRepository.findByUsername(username).orElseThrow()
            assertThat(persistedUser.password)
                .describedAs("Board-created users should still receive a generated password hash")
                .isNotBlank()
        }
    }

    @Nested
    inner class UpdateUser {
        @Test
        fun `board can update guest user with board payload`() {
            val board = createUserWithRole(Role.BOARD)
            val guest = createUserWithRole(Role.GUEST)
            val updatedUsername = "integration_user_updated_${System.currentTimeMillis()}"
            val updatedEmail = "$updatedUsername@example.com"

            mvc.perform(
                put("/users/{id}", guest.id)
                    .with(bearer(board))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"kind":"board","username":"$updatedUsername","initials":"IU","firstName":"Updated","lastName":"User","newsletter":false,"email":"$updatedEmail","discord":"updated#1234","phoneNumber":"+31612345679","version":${guest.version}}"""
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(guest.id))
                .andExpect(jsonPath("$.username").value(updatedUsername))
                .andExpect(jsonPath("$.firstName").value("Updated"))
                .andExpect(jsonPath("$.discord").value("updated#1234"))

            val jobs = findJobsByType(ContactJobs.SyncContact.type)
            assertThat(jobs)
                .describedAs("Should schedule contact sync job on user update")
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.payload).contains("\"userId\":${guest.id}")
                }
        }

        @Test
        fun `guest can update own guest profile`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/{id}", guest.id)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"user","discord":"guest_self_updated#1234","phoneNumber":"+31612345670","newsletter":false,"version":${guest.version}}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(guest.id))
                .andExpect(jsonPath("$.discord").value("guest_self_updated#1234"))
                .andExpect(jsonPath("$.phoneNumber").value("+31612345670"))
        }

        @Test
        fun `user update upserts member profile when missing`() {
            val guest = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/{id}", guest.id)
                    .with(bearer(guest))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"kind":"user","discord":"guest_upserted#1234","phoneNumber":"+31612345671","newsletter":false,"version":${guest.version},"memberProfile":{"dateOfBirth":"2000-06-15","studentNumber":"s7654321","gender":"F","photoConsent":false,"nationality":"DE","bhv":false,"ehbo":true}}"""
                    )
            )
                .andExpect(status().isOk)

            val profile = memberProfileRepository.findById(guest.id!!).orElseThrow()
            assertThat(profile.studentNumber).isEqualTo("s7654321")
            assertThat(profile.gender).isEqualTo("F")
            assertThat(profile.photoConsent).isFalse()
            assertThat(profile.nationality).isEqualTo("DE")
            assertThat(profile.bhv).isFalse()
            assertThat(profile.ehbo).isTrue()
            assertThat(profile.dateOfBirth.toLocalDate().toString()).isEqualTo("2000-06-15")
        }

        @Test
        fun `user update replaces existing member profile data`() {
            val guestWithProfile = assignMemberProfile(createUserWithRole(Role.GUEST))
            val profileVersionBefore = memberProfileRepository.findById(guestWithProfile.id!!).orElseThrow().version
            entityManager.clear()

            mvc.perform(
                put("/users/{id}", guestWithProfile.id)
                    .with(bearer(guestWithProfile))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"kind":"user","discord":"guest_profile_updated#1234","phoneNumber":"+31612345672","newsletter":false,"version":${guestWithProfile.version},"memberProfile":{"dateOfBirth":"2001-01-20","studentNumber":"s1111111","gender":"M","photoConsent":false,"nationality":"FR","bhv":true,"ehbo":true,"version":$profileVersionBefore}}"""
                    )
            )
                .andExpect(status().isOk)

            val profileAfter = memberProfileRepository.findById(guestWithProfile.id!!).orElseThrow()
            assertThat(profileAfter.studentNumber).isEqualTo("s1111111")
            assertThat(profileAfter.gender).isEqualTo("M")
            assertThat(profileAfter.photoConsent).isFalse()
            assertThat(profileAfter.nationality).isEqualTo("FR")
            assertThat(profileAfter.bhv).isTrue()
            assertThat(profileAfter.ehbo).isTrue()
            assertThat(profileAfter.dateOfBirth.toLocalDate().toString()).isEqualTo("2001-01-20")
            assertThat(profileAfter.version).isGreaterThan(profileVersionBefore)
        }

        @Test
        fun `user update without member profile keeps existing member profile`() {
            val guestWithProfile = assignMemberProfile(createUserWithRole(Role.GUEST))
            val profileBefore = memberProfileRepository.findById(guestWithProfile.id!!).orElseThrow()
            val studentNumberBefore = profileBefore.studentNumber

            mvc.perform(
                put("/users/{id}", guestWithProfile.id)
                    .with(bearer(guestWithProfile))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"kind":"user","discord":"guest_no_profile_change#1234","phoneNumber":"+31612345673","newsletter":false,"version":${guestWithProfile.version}}""")
            )
                .andExpect(status().isOk)

            val profileAfter = memberProfileRepository.findById(guestWithProfile.id!!).orElseThrow()
            assertThat(profileAfter.studentNumber).isEqualTo(studentNumberBefore)
        }
    }

    @Nested
    inner class FindUsers {
        @Test
        fun `board can list users`() {
            val board = createUserWithRole(Role.BOARD)
            createUserWithRole(Role.MEMBER)

            mvc.perform(get("/users").with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.content").isArray)
        }
    }

    @Nested
    inner class FindUserById {
        @Test
        fun `board can get user by id`() {
            val board = createUserWithRole(Role.BOARD)
            val target = createUserWithRole(Role.MEMBER)

            mvc.perform(get("/users/{userId}", target.id).with(bearer(board)))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.id").value(target.id))
        }
    }

    @Nested
    inner class DeleteUserById {
        @Test
        fun `board can delete user`() {
            val board = createUserWithRole(Role.BOARD)
            val target = createUserWithRole(Role.MEMBER)

            mvc.perform(delete("/users/{userId}", target.id).with(bearer(board)))
                .andExpect(status().isNoContent)

            assertThat(userRepository.findById(target.id!!)).isEmpty
        }
    }

    @Nested
    inner class ToggleUserRole {
        @Test
        fun `admin can toggle user role`() {
            val admin = createUserWithRole(Role.ADMIN)
            val createdUser = createUserWithRole(Role.GUEST)

            mvc.perform(
                put("/users/{userId}/roles", createdUser.id)
                    .param("role", "MEMBER")
                    .with(bearer(admin))
            )
                .andExpect(status().isOk)

            assertThat(userRepository.findById(createdUser.id!!).orElseThrow().roles).contains(Role.MEMBER)
            val jobs = findJobsByType(ContactJobs.SyncContact.type)
            assertThat(jobs)
                .describedAs("Should schedule contact sync job on role toggle")
                .hasSize(1)
                .anySatisfy {
                    assertThat(it.payload).contains("\"userId\":${createdUser.id}")
                }
        }
    }
}
