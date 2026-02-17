package net.blueshell.api.domain.user.web

import net.blueshell.api.domain.user.persistence.repository.MemberProfileRepository
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
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

    private fun createGuestPayload(username: String, email: String): String =
        """{"username":"$username","initials":"GU","firstName":"Guest","lastName":"User","newsletter":true,"password":"Password123!","email":"$email","discord":"guest#1234","phoneNumber":"+31612345678"}"""

    @Test
    fun `creates and updates user profile as board`() {
        val board = createUserWithRole(Role.BOARD)

        val createResult = mvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"username":"integration_user_${System.currentTimeMillis()}","initials":"IU","firstName":"Integration","lastName":"User","newsletter":true,"password":"Password123!","email":"integration_user_${System.currentTimeMillis()}@example.com","discord":"integration#1234","phoneNumber":"+31612345678"}"""
                )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val userId = created.path("id").asLong()
        val updatedUsername = "integration_user_updated_${System.currentTimeMillis()}"
        val updatedEmail = "$updatedUsername@example.com"
        val version = created.path("version").asLong()

        mvc.perform(
            put("/users/{id}", userId)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """{"kind":"board","username":"$updatedUsername","initials":"IU","firstName":"Updated","lastName":"User","newsletter":false,"nationality":"Dutch","email":"$updatedEmail","discord":"updated#1234","phoneNumber":"+31612345679","version":$version}"""
                )
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.firstName").value("Updated"))
            .andExpect(jsonPath("$.discord").value("updated#1234"))
    }

    @Test
    fun `creates guest user publicly`() {
        val guestUsername = "guest_it_${System.currentTimeMillis()}"
        val guestEmail = "$guestUsername@example.com"

        mvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createGuestPayload(guestUsername, guestEmail))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andExpect(jsonPath("$.username").value(guestUsername))
            .andExpect(jsonPath("$.email").value(guestEmail))
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
    fun `board can update guest user`() {
        val board = createUserWithRole(Role.BOARD)
        val guestUsername = "guest_it_${System.currentTimeMillis()}"
        val guestEmail = "$guestUsername@example.com"

        val createResult = mvc.perform(
            post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createGuestPayload(guestUsername, guestEmail))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").isNotEmpty)
            .andReturn()

        val created = mapper.readTree(createResult.response.contentAsByteArray)
        val userId = created.path("id").asLong()
        val version = created.path("version").asLong()

        mvc.perform(
            put("/users/{id}", userId)
                .with(bearer(board))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"kind":"user","discord":"guest_updated#1234","phoneNumber":"+31612345679","newsletter":false,"version":$version}""")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(userId))
            .andExpect(jsonPath("$.discord").value("guest_updated#1234"))
            .andExpect(jsonPath("$.phoneNumber").value("+31612345679"))
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
    fun `board can list and delete users`() {
        val board = createUserWithRole(Role.BOARD)
        val createdUser = createUserWithRole(Role.MEMBER)

        mvc.perform(get("/users").with(bearer(board)))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.content").isArray)

        mvc.perform(delete("/users/{userId}", createdUser.id).with(bearer(board)))
            .andExpect(status().isNoContent)

        mvc.perform(get("/users/{userId}", createdUser.id).with(bearer(board)))
            .andExpect(status().isNotFound)
    }

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
    }
}
