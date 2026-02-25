package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.erasure.UserErasureService
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.domain.user.command.BoardUpdateUserCommand
import net.blueshell.api.domain.user.command.CreateUserCommand
import net.blueshell.api.domain.user.command.DeleteUserByIdCommand
import net.blueshell.api.domain.user.command.FindUserByIdCommand
import net.blueshell.api.domain.user.command.FindDeletedUsersCommand
import net.blueshell.api.domain.user.command.FindUsersCommand
import net.blueshell.api.domain.user.command.RestoreDeletedUserByIdCommand
import net.blueshell.api.domain.user.command.ToggleUserRoleCommand
import net.blueshell.api.domain.user.command.UpdateUserCommand
import net.blueshell.api.domain.user.command.UpsertMemberProfileData
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.DeletedUser
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import java.sql.Date

class UserCommandHandlersTest {

    private val userService = mock<UserService>()
    private val erasure = mock<UserErasureService>()
    private val passwordEncoder = mock<PasswordEncoder>()

    @Nested
    inner class CreateUser {

        private val handler = CreateUserHandler(userService, passwordEncoder)

        @Test
        fun `creates non board user with encoded provided password and member profile`() {
            whenever(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-pass")
            val captured = argumentCaptor<User>()
            whenever(userService.create(captured.capture())).thenAnswer { captured.firstValue }
            val command = CreateUserCommand(
                isBoard = false,
                username = "john",
                email = "john@example.com",
                initials = "JD",
                firstName = "John",
                prefix = null,
                lastName = "Doe",
                newsletter = true,
                consentPrivacy = true,
                photoConsent = true,
                password = "Passw0rd!",
                discord = "john#0001",
                phoneNumber = "0612345678",
                memberProfile = upsertMemberProfileData(version = null)
            )

            val result = handler.handle(command)

            assertThat(captured.firstValue.username).isEqualTo("john")
            assertThat(captured.firstValue.email).isEqualTo("john@example.com")
            assertThat(captured.firstValue.password).isEqualTo("encoded-pass")
            assertThat(captured.firstValue.consentPrivacy).isTrue()
            assertThat(captured.firstValue.photoConsent).isTrue()
            assertThat(captured.firstValue.memberProfile).isNotNull
            assertThat(captured.firstValue.memberProfile?.studentNumber).isEqualTo("s123")
            assertThat(captured.firstValue.memberProfile?.dateOfBirth).isEqualTo(Date.valueOf("2000-01-01"))
            assertThat(result).isSameAs(captured.firstValue)
            verify(passwordEncoder).encode("Passw0rd!")
        }

        @Test
        fun `creates board user with generated encoded password`() {
            whenever(passwordEncoder.encode(any())).thenReturn("encoded-board-pass")
            val capturedUser = argumentCaptor<User>()
            val capturedPassword = argumentCaptor<CharSequence>()
            whenever(userService.create(capturedUser.capture())).thenAnswer { capturedUser.firstValue }
            val command = CreateUserCommand(
                isBoard = true,
                username = "board",
                email = "board@example.com",
                initials = "BD",
                firstName = "Board",
                prefix = null,
                lastName = "User",
                newsletter = false,
                consentPrivacy = false,
                photoConsent = false,
                password = null,
                discord = "board#0001",
                phoneNumber = "0611111111",
                memberProfile = null
            )

            val result = handler.handle(command)

            verify(passwordEncoder).encode(capturedPassword.capture())
            assertThat(capturedPassword.firstValue).isNotNull
            assertThat(capturedPassword.firstValue.toString()).isNotBlank()
            assertThat(result.password).isEqualTo("encoded-board-pass")
        }

        @Test
        fun `rejects non board create when password is missing`() {
            val command = CreateUserCommand(
                isBoard = false,
                username = "john",
                email = "john@example.com",
                initials = "JD",
                firstName = "John",
                prefix = null,
                lastName = "Doe",
                newsletter = true,
                consentPrivacy = false,
                photoConsent = false,
                password = null,
                discord = "john#0001",
                phoneNumber = "0612345678",
                memberProfile = null
            )

            val thrown = assertThrows<IllegalArgumentException> {
                handler.handle(command)
            }

            assertThat(thrown.message).contains("Password is required")
        }
    }

    @Nested
    inner class BoardUpdateUser {

        private val handler = BoardUpdateUserHandler(userService)

        @Test
        fun `updates all board editable fields and creates member profile when missing`() {
            val existing = testUser("john")
            whenever(userService.findById(1L)).thenReturn(existing)
            whenever(userService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                BoardUpdateUserCommand(
                    id = 1L,
                    username = "newuser",
                    email = "new@example.com",
                    initials = "NU",
                    firstName = "New",
                    prefix = "van",
                    lastName = "User",
                    newsletter = false,
                    photoConsent = true,
                    discord = "new#0001",
                    phoneNumber = "0622222222",
                    version = 4L,
                    memberProfile = upsertMemberProfileData(version = null)
                )
            )

            assertThat(existing.username).isEqualTo("newuser")
            assertThat(existing.email).isEqualTo("new@example.com")
            assertThat(existing.initials).isEqualTo("NU")
            assertThat(existing.firstName).isEqualTo("New")
            assertThat(existing.prefix).isEqualTo("van")
            assertThat(existing.lastName).isEqualTo("User")
            assertThat(existing.newsletter).isFalse()
            assertThat(existing.photoConsent).isTrue()
            assertThat(existing.discord).isEqualTo("new#0001")
            assertThat(existing.phoneNumber).isEqualTo("0622222222")
            assertThat(existing.version).isEqualTo(4L)
            assertThat(existing.memberProfile).isNotNull
            assertThat(existing.memberProfile?.studentNumber).isEqualTo("s123")
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class UpdateUser {

        private val handler = UpdateUserHandler(userService)

        @Test
        fun `updates own fields and existing member profile`() {
            val existing = testUser("john")
            val existingProfile = MemberProfile(
                user = existing,
                dateOfBirth = Date.valueOf("1999-01-01"),
                studentNumber = "old",
                gender = "F",
                nationality = "Dutch",
                bhv = false,
                ehbo = false
            )
            existing.replaceMemberProfile(existingProfile)
            whenever(userService.findById(2L)).thenReturn(existing)
            whenever(userService.update(existing)).thenReturn(existing)

            val result = handler.handle(
                UpdateUserCommand(
                    id = 2L,
                    discord = "upd#0001",
                    phoneNumber = "0633333333",
                    newsletter = true,
                    photoConsent = true,
                    version = 8L,
                    memberProfile = upsertMemberProfileData(version = 9L)
                )
            )

            assertThat(existing.discord).isEqualTo("upd#0001")
            assertThat(existing.phoneNumber).isEqualTo("0633333333")
            assertThat(existing.newsletter).isTrue()
            assertThat(existing.photoConsent).isTrue()
            assertThat(existing.version).isEqualTo(8L)
            assertThat(existing.memberProfile?.studentNumber).isEqualTo("s123")
            assertThat(existing.memberProfile?.version).isEqualTo(9L)
            assertThat(result).isSameAs(existing)
        }
    }

    @Nested
    inner class FindUsers {

        private val handler = FindUsersHandler(userService)

        @Test
        fun `returns users page by query and pageable`() {
            val query = UserQuery(username = "john")
            val pageable = PageRequest.of(0, 10)
            val page = PageImpl(listOf(testUser("john")), pageable, 1)
            whenever(userService.findByQuery(query, pageable)).thenReturn(page)

            val result = handler.handle(FindUsersCommand(filter = query, pageable = pageable))

            assertThat(result).isSameAs(page)
            verify(userService).findByQuery(query, pageable)
        }
    }

    @Nested
    inner class FindUserById {

        private val handler = FindUserByIdHandler(userService)

        @Test
        fun `returns user by id`() {
            val expected = testUser("john")
            whenever(userService.findById(3L)).thenReturn(expected)

            val result = handler.handle(FindUserByIdCommand(3L))

            assertThat(result).isSameAs(expected)
            verify(userService).findById(3L)
        }
    }

    @Nested
    inner class DeleteUserById {

        private val handler = DeleteUserByIdHandler(erasure)

        @Test
        fun `deletes user by id`() {
            handler.handle(DeleteUserByIdCommand(4L))

            verify(erasure).deleteUser(eq(4L))
        }
    }

    @Nested
    inner class FindDeletedUsers {

        private val handler = FindDeletedUsersHandler(erasure)

        @Test
        fun `returns deleted users by pageable`() {
            val pageable = PageRequest.of(0, 10)
            val page = PageImpl(
                listOf(
                    DeletedUser(
                        userId = 8L,
                        username = "restorable",
                        email = "restorable@example.com",
                        initials = "RS",
                        firstName = "Rest",
                        prefix = null,
                        lastName = "Orable",
                        phoneNumber = null,
                        discord = null,
                        newsletter = false,
                        photoConsent = false,
                        enabled = true,
                        deletedAt = java.time.Instant.now(),
                        restoreUntilAt = java.time.Instant.now().plusSeconds(3600)
                    )
                ),
                pageable,
                1
            )
            whenever(erasure.findDeletedUsers(pageable)).thenReturn(page)

            val result = handler.handle(FindDeletedUsersCommand(pageable))

            assertThat(result).isSameAs(page)
            verify(erasure).findDeletedUsers(pageable)
        }
    }

    @Nested
    inner class RestoreDeletedUserById {
        private val handler = RestoreDeletedUserByIdHandler(erasure)

        @Test
        fun `restores user by id`() {
            handler.handle(RestoreDeletedUserByIdCommand(9L))
            verify(erasure).restoreDeletedUser(9L)
        }
    }

    @Nested
    inner class ToggleUserRole {

        private val handler = ToggleUserRoleHandler(userService)

        @Test
        fun `toggles user role`() {
            val expected = testUser("john")
            whenever(userService.toggleRole(5L, Role.BOARD)).thenReturn(expected)

            val result = handler.handle(ToggleUserRoleCommand(5L, Role.BOARD))

            assertThat(result).isSameAs(expected)
            verify(userService).toggleRole(5L, Role.BOARD)
        }
    }

    private fun upsertMemberProfileData(version: Long?) = UpsertMemberProfileData(
        dateOfBirth = Date.valueOf("2000-01-01"),
        studentNumber = "s123",
        gender = "M",
        nationality = "Dutch",
        bhv = false,
        ehbo = true,
        version = version
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
