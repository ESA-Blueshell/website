package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.sql.Date

class MemberProfileUseCasesTest {

    private val userService = mock<UserService>()
    private val memberProfileService = mock<MemberProfileService>()
    private val useCases = MemberProfileUseCases(memberProfileService, userService)

    @Nested
    inner class Create {

        @Test
        fun `creates member profile and links it to user`() {
            val user = testUser("john")
            whenever(userService.findById(1L)).thenReturn(user)
            whenever(userService.update(user)).thenReturn(user)
            val birthDate = Date.valueOf("2000-01-01")

            val result = useCases.create(
                userId = 1L,
                dateOfBirth = birthDate,
                studentNumber = "s123",
                gender = "M",
                nationality = "Dutch",
                bhv = false,
                ehbo = true
            )

            assertThat(result.user).isSameAs(user)
            assertThat(result.dateOfBirth).isEqualTo(birthDate)
            assertThat(result.studentNumber).isEqualTo("s123")
            assertThat(result.gender).isEqualTo("M")
            assertThat(result.nationality).isEqualTo("Dutch")
            assertThat(result.bhv).isFalse()
            assertThat(result.ehbo).isTrue()
        }

        @Test
        fun `throws conflict when user already has member profile`() {
            val user = testUser("john")
            user.replaceMemberProfile(profileFor(user, studentNumber = "old"))
            whenever(userService.findById(1L)).thenReturn(user)

            assertThatThrownBy {
                useCases.create(
                    userId = 1L,
                    dateOfBirth = Date.valueOf("2000-01-01"),
                    studentNumber = "s123",
                    gender = "M",
                    nationality = "Dutch",
                    bhv = false,
                    ehbo = true
                )
            }.isInstanceOf(ResponseStatusException::class.java)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `updates member profile fields and version`() {
            val profile = profileFor(testUser("john"), studentNumber = "old")
            whenever(memberProfileService.findById(1L)).thenReturn(profile)
            whenever(memberProfileService.update(profile)).thenReturn(profile)
            val birthDate = Date.valueOf("2000-01-01")

            val result = useCases.update(
                userId = 1L,
                dateOfBirth = birthDate,
                studentNumber = "new",
                gender = "M",
                nationality = "Belgian",
                bhv = true,
                ehbo = true,
                version = 6L
            )

            assertThat(profile.dateOfBirth).isEqualTo(birthDate)
            assertThat(profile.studentNumber).isEqualTo("new")
            assertThat(profile.gender).isEqualTo("M")
            assertThat(profile.nationality).isEqualTo("Belgian")
            assertThat(profile.bhv).isTrue()
            assertThat(profile.ehbo).isTrue()
            assertThat(profile.version).isEqualTo(6L)
            assertThat(result).isSameAs(profile)
        }
    }

    @Nested
    inner class FindByUserId {

        @Test
        fun `returns member profile for user`() {
            val user = testUser("john")
            val profile = profileFor(user, studentNumber = "s123")
            user.replaceMemberProfile(profile)
            whenever(userService.findById(2L)).thenReturn(user)

            assertThat(useCases.findByUserId(2L)).isSameAs(profile)
        }

        @Test
        fun `throws not found when user has no member profile`() {
            whenever(userService.findById(2L)).thenReturn(testUser("john"))

            assertThatThrownBy { useCases.findByUserId(2L) }
                .isInstanceOf(ResponseStatusException::class.java)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun profileFor(user: User, studentNumber: String) = MemberProfile(
        user = user,
        dateOfBirth = Date.valueOf("1998-01-01"),
        studentNumber = studentNumber,
        gender = "F",
        nationality = "Dutch",
        bhv = false,
        ehbo = false
    )

    private fun testUser(username: String) = User(
        username = username,
        email = "$username@example.com",
        password = "encoded",
        initials = "JD",
        firstName = "John",
        prefix = null,
        lastName = "Doe",
        phoneNumber = "0612345678",
        discord = "john#0001",
        newsletter = true
    )
}
