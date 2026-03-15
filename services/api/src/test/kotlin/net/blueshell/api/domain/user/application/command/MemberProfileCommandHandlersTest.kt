package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.command.CreateMemberProfileCommand
import net.blueshell.api.domain.user.command.FindMemberProfileByUserIdCommand
import net.blueshell.api.domain.user.command.UpdateMemberProfileCommand
import net.blueshell.api.domain.user.persistence.MemberProfile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.sql.Date
import java.time.LocalDate

class MemberProfileCommandHandlersTest {

    private val userService = mock<UserService>()
    private val memberProfileService = mock<MemberProfileService>()

    @Nested
    inner class CreateMemberProfile {

        private val handler = CreateMemberProfileHandler(userService)

        @Test
        fun `creates member profile and links it to user`() {
            val user = testUser("john")
            whenever(userService.findById(1L)).thenReturn(user)
            whenever(userService.update(user)).thenReturn(user)
            val birthDate = Date.valueOf("2000-01-01")

            val result = handler.handle(
                CreateMemberProfileCommand(
                    userId = 1L,
                    dateOfBirth = birthDate,
                    studentNumber = "s123",
                    gender = "M",
                    nationality = "Dutch",
                    bhv = false,
                    ehbo = true
                )
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
            user.replaceMemberProfile(
                MemberProfile(
                    user = user,
                    dateOfBirth = Date.valueOf("2000-01-01"),
                    studentNumber = "old",
                    gender = "F",
                    nationality = "Dutch",
                    bhv = false,
                    ehbo = false
                )
            )
            whenever(userService.findById(1L)).thenReturn(user)

            assertThatThrownBy {
                handler.handle(
                    CreateMemberProfileCommand(
                        userId = 1L,
                        dateOfBirth = Date.valueOf("2000-01-01"),
                        studentNumber = "s123",
                        gender = "M",
                        nationality = "Dutch",
                        bhv = false,
                        ehbo = true
                    )
                )
            }.isInstanceOf(ResponseStatusException::class.java)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.CONFLICT)
        }
    }

    @Nested
    inner class UpdateMemberProfile {

        private val handler = UpdateMemberProfileHandler(memberProfileService)

        @Test
        fun `updates member profile fields and version`() {
            val user = testUser("john")
            val profile = MemberProfile(
                user = user,
                dateOfBirth = Date.valueOf("1998-01-01"),
                studentNumber = "old",
                gender = "F",
                nationality = "Dutch",
                bhv = false,
                ehbo = false
            )
            whenever(memberProfileService.findById(1L)).thenReturn(profile)
            whenever(memberProfileService.update(profile)).thenReturn(profile)
            val birthDate = Date.valueOf("2000-01-01")

            val result = handler.handle(
                UpdateMemberProfileCommand(
                    userId = 1L,
                    dateOfBirth = birthDate,
                    studentNumber = "new",
                    gender = "M",
                    nationality = "Belgian",
                    bhv = true,
                    ehbo = true,
                    version = 6L
                )
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
    inner class FindMemberProfileByUserId {

        private val handler = FindMemberProfileByUserIdHandler(userService)

        @Test
        fun `returns member profile for user`() {
            val user = testUser("john")
            val profile = MemberProfile(
                user = user,
                dateOfBirth = Date.valueOf("1998-01-01"),
                studentNumber = "s123",
                gender = "M",
                nationality = "Dutch",
                bhv = false,
                ehbo = false
            )
            user.replaceMemberProfile(profile)
            whenever(userService.findById(2L)).thenReturn(user)

            val result = handler.handle(FindMemberProfileByUserIdCommand(2L))

            assertThat(result).isSameAs(profile)
        }

        @Test
        fun `throws not found when user has no member profile`() {
            val user = testUser("john")
            whenever(userService.findById(2L)).thenReturn(user)

            assertThatThrownBy {
                handler.handle(FindMemberProfileByUserIdCommand(2L))
            }.isInstanceOf(ResponseStatusException::class.java)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.NOT_FOUND)
        }
    }

    private fun testUser(username: String) = net.blueshell.api.domain.user.persistence.User(
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
