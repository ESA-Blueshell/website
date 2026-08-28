package net.blueshell.api.user.domain

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.enums.Role
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.check
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.security.crypto.password.PasswordEncoder
import java.sql.Date
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserUseCases
import net.blueshell.api.user.api.BoardUserData
import net.blueshell.api.user.api.NewUserData
import net.blueshell.api.user.api.SelfUserData
import net.blueshell.api.user.api.UpsertMemberProfileData

class UserUseCasesTest {

    private val userService = mock<UserService>()
    private val erasure = mock<UserErasureService>()
    private val passwordEncoder = mock<PasswordEncoder>()
    private val validator = mock<Validator>()

    private val useCases = UserUseCases(userService, erasure, passwordEncoder, validator)

    @BeforeEach
    fun noViolations() {
        whenever(validator.validate(any<UserRegistration>())).thenReturn(mutableSetOf())
        whenever(validator.validate(any<UserUniqueness>())).thenReturn(mutableSetOf())
    }

    @Nested
    inner class Create {

        @Test
        fun `creates non board user with encoded provided password and member profile`() {
            whenever(passwordEncoder.encode("Passw0rd!")).thenReturn("encoded-pass")
            val captured = argumentCaptor<User>()
            whenever(userService.create(captured.capture())).thenAnswer { captured.firstValue }

            val result = useCases.create(
                NewUserData(
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
                ),
                isBoard = false,
            )

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

            val result = useCases.create(boardData(), isBoard = true)

            verify(passwordEncoder).encode(capturedPassword.capture())
            assertThat(capturedPassword.firstValue).isNotNull
            assertThat(capturedPassword.firstValue.toString()).isNotBlank()
            assertThat(result.password).isEqualTo("encoded-board-pass")
        }

        @Test
        fun `rejects non board create when password is missing`() {
            val thrown = assertThrows<IllegalArgumentException> {
                useCases.create(boardData().copy(password = null), isBoard = false)
            }

            assertThat(thrown.message).contains("Password is required")
        }

        @Test
        fun `checks the registration rules against the route it came in on`() {
            whenever(passwordEncoder.encode(any())).thenReturn("encoded")
            whenever(userService.create(any())).thenAnswer { it.arguments[0] }

            useCases.create(boardData(), isBoard = true)

            verify(validator).validate(check<UserRegistration> {
                assertThat(it.isBoard).isTrue()
                assertThat(it.username).isEqualTo("board")
                assertThat(it.email).isEqualTo("board@example.com")
                assertThat(it.subjectId).isNull()
            })
        }

        @Test
        fun `refuses a registration the rules reject, before creating anything`() {
            whenever(validator.validate(any<UserRegistration>()))
                .thenReturn(mutableSetOf(mock<ConstraintViolation<UserRegistration>>()))

            assertThatThrownBy { useCases.create(boardData(), isBoard = false) }
                .isInstanceOf(ConstraintViolationException::class.java)
            verify(userService, never()).create(any())
        }

        private fun boardData() = NewUserData(
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
    }

    @Nested
    inner class BoardUpdate {

        @Test
        fun `updates all board editable fields and creates member profile when missing`() {
            val existing = testUser("john")
            whenever(userService.findById(1L)).thenReturn(existing)
            whenever(userService.update(existing)).thenReturn(existing)

            val result = useCases.boardUpdate(
                1L,
                BoardUserData(
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

        @Test
        fun `checks uniqueness excluding the row being edited`() {
            val existing = testUser("john")
            whenever(userService.findById(1L)).thenReturn(existing)
            whenever(userService.update(existing)).thenReturn(existing)

            useCases.boardUpdate(
                1L,
                BoardUserData(
                    username = "newuser",
                    email = "new@example.com",
                    initials = "NU",
                    firstName = "New",
                    prefix = null,
                    lastName = "User",
                    newsletter = false,
                    photoConsent = false,
                    discord = "new#0001",
                    phoneNumber = "0622222222",
                    version = 4L,
                )
            )

            verify(validator).validate(check<UserUniqueness> {
                assertThat(it.subjectId).isEqualTo(1L)
                assertThat(it.username).isEqualTo("newuser")
                assertThat(it.email).isEqualTo("new@example.com")
            })
        }
    }

    @Nested
    inner class Update {

        @Test
        fun `updates own fields and existing member profile`() {
            val existing = testUser("john")
            existing.replaceMemberProfile(
                MemberProfile(
                    user = existing,
                    dateOfBirth = Date.valueOf("1999-01-01"),
                    studentNumber = "old",
                    gender = "F",
                    nationality = "Dutch",
                    bhv = false,
                    ehbo = false
                )
            )
            whenever(userService.findById(2L)).thenReturn(existing)
            whenever(userService.update(existing)).thenReturn(existing)

            val result = useCases.update(
                2L,
                SelfUserData(
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

        @Test
        fun `checks only the two fields the self-service shape can change`() {
            val existing = testUser("john")
            whenever(userService.findById(2L)).thenReturn(existing)
            whenever(userService.update(existing)).thenReturn(existing)

            useCases.update(
                2L,
                SelfUserData(
                    discord = "upd#0001",
                    phoneNumber = "0633333333",
                    newsletter = true,
                    photoConsent = true,
                    version = 8L,
                )
            )

            verify(validator).validate(check<UserUniqueness> {
                assertThat(it.subjectId).isEqualTo(2L)
                assertThat(it.discord).isEqualTo("upd#0001")
                assertThat(it.phoneNumber).isEqualTo("0633333333")
                assertThat(it.username).isNull()
                assertThat(it.email).isNull()
            })
        }
    }

    @Nested
    inner class FindByQuery {

        @Test
        fun `returns users page by query and pageable`() {
            val query = UserQuery(username = "john")
            val pageable = PageRequest.of(0, 10)
            val page = PageImpl(listOf(testUser("john")), pageable, 1)
            whenever(userService.findByQuery(query, pageable)).thenReturn(page)

            assertThat(useCases.findByQuery(query, pageable)).isSameAs(page)
            verify(userService).findByQuery(query, pageable)
        }
    }

    @Nested
    inner class FindById {

        @Test
        fun `returns user by id`() {
            val expected = testUser("john")
            whenever(userService.findById(3L)).thenReturn(expected)

            assertThat(useCases.findById(3L)).isSameAs(expected)
            verify(userService).findById(3L)
        }
    }

    @Nested
    inner class Delete {

        @Test
        fun `deletes user by id`() {
            useCases.delete(4L)

            verify(erasure).deleteUser(eq(4L))
        }
    }

    @Nested
    inner class FindDeleted {

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

            assertThat(useCases.findDeleted(pageable)).isSameAs(page)
            verify(erasure).findDeletedUsers(pageable)
        }
    }

    @Nested
    inner class Restore {

        @Test
        fun `restores user by id`() {
            useCases.restore(9L)
            verify(erasure).restoreDeletedUser(9L)
        }
    }

    @Nested
    inner class ToggleRole {

        @Test
        fun `toggles user role`() {
            val expected = testUser("john")
            whenever(userService.toggleRole(5L, Role.BOARD)).thenReturn(expected)

            assertThat(useCases.toggleRole(5L, Role.BOARD)).isSameAs(expected)
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
