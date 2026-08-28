package net.blueshell.api.domain.user.application

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.auth.domain.SignupCompletionService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.application.validation.MembershipInterval
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.SignupOutcome
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDate

class MembershipUseCasesTest {

    private val membershipService = mock<MembershipService>()
    private val userService = mock<UserService>()
    private val memberProfiles = mock<MemberProfileService>()
    private val completion = mock<SignupCompletionService>()
    private val validator = mock<Validator>()

    private val useCases = MembershipUseCases(
        membershipService,
        userService,
        memberProfiles,
        completion,
        validator,
    )

    private fun noViolations() {
        whenever(validator.validate(any<MembershipInterval>())).thenReturn(mutableSetOf())
    }

    private fun withViolation() {
        whenever(validator.validate(any<MembershipInterval>()))
            .thenReturn(mutableSetOf(mock<ConstraintViolation<MembershipInterval>>()))
    }

    @Nested
    inner class FindByQuery {

        @Test
        fun `returns memberships by query`() {
            val query = MembershipQuery(from = LocalDate.of(2025, 1, 1))
            val expected = mutableListOf(Membership(user = testUser("expected"), startDate = LocalDate.now()))
            whenever(membershipService.findByQuery(query)).thenReturn(expected)

            assertThat(useCases.findByQuery(query)).isSameAs(expected)
            verify(membershipService).findByQuery(query)
        }
    }

    @Nested
    inner class Apply {

        private fun applicantWithProfile(): MemberProfile {
            val user = testUser("john")
            val profile = MemberProfile(user = user, bhv = false, ehbo = false)
            user.replaceMemberProfile(profile)
            whenever(userService.findById(1L)).thenReturn(user)
            return profile
        }

        @Test
        fun `stamps the acceptance and returns the outcome`() {
            noViolations()
            val profile = applicantWithProfile()
            whenever(completion.completeIfReady(1L))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val outcome = useCases.apply(1L)

            assertThat(profile.conditionsAcceptedAt).isNotNull()
            verify(memberProfiles).update(profile)
            assertThat(outcome.membershipStarted).isTrue()
        }

        @Test
        fun `refuses an application the completion rule cannot commit`() {
            noViolations()
            applicantWithProfile()
            whenever(completion.completeIfReady(1L))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = false))

            assertThatThrownBy { useCases.apply(1L) }
                .isInstanceOf(AccessDeniedException::class.java)
        }

        @Test
        fun `refuses an account with no member profile`() {
            noViolations()
            whenever(userService.findById(1L)).thenReturn(testUser("john"))

            assertThatThrownBy { useCases.apply(1L) }
                .isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("Complete profile is required")
        }

        @Test
        fun `refuses an application that would overlap an existing membership`() {
            withViolation()

            assertThatThrownBy { useCases.apply(1L) }
                .isInstanceOf(ConstraintViolationException::class.java)
            verify(memberProfiles, never()).update(any())
        }
    }

    @Nested
    inner class BoardCreate {

        @Test
        fun `creates membership from the supplied fields`() {
            noViolations()
            val user = testUser("john")
            whenever(userService.findById(2L)).thenReturn(user)
            val startDate = LocalDate.of(2025, 1, 1)
            val endDate = LocalDate.of(2025, 12, 31)
            val expected = Membership(user = user, startDate = startDate)
            whenever(membershipService.create(any())).thenReturn(expected)

            val result = useCases.boardCreate(
                userId = 2L,
                memberType = MemberType.REGULAR,
                startDate = startDate,
                endDate = endDate,
                incasso = true,
            )

            assertThat(result).isSameAs(expected)
            verify(membershipService).create(check {
                assertThat(it.user).isSameAs(user)
                assertThat(it.memberType).isEqualTo(MemberType.REGULAR)
                assertThat(it.startDate).isEqualTo(startDate)
                assertThat(it.endDate).isEqualTo(endDate)
                assertThat(it.incasso).isTrue()
            })
        }

        @Test
        fun `throws when the interval is invalid`() {
            withViolation()

            assertThatThrownBy {
                useCases.boardCreate(
                    userId = 2L,
                    memberType = MemberType.REGULAR,
                    startDate = LocalDate.of(2025, 1, 1),
                    endDate = null,
                    incasso = true,
                )
            }.isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).create(any())
        }
    }

    @Nested
    inner class Correct {

        @Test
        fun `corrects membership fields and version`() {
            noViolations()
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.of(2024, 1, 1),
                memberType = MemberType.ALUMNI,
                endDate = null,
                incasso = false,
            ).apply { version = 1L }
            whenever(membershipService.findById(3L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)

            val result = useCases.correct(
                id = 3L,
                userId = 2L,
                memberType = MemberType.HONORARY,
                startDate = LocalDate.of(2025, 1, 1),
                endDate = LocalDate.of(2025, 12, 31),
                incasso = true,
                version = 5L,
            )

            assertThat(membership.memberType).isEqualTo(MemberType.HONORARY)
            assertThat(membership.startDate).isEqualTo(LocalDate.of(2025, 1, 1))
            assertThat(membership.endDate).isEqualTo(LocalDate.of(2025, 12, 31))
            assertThat(membership.incasso).isTrue()
            assertThat(membership.version).isEqualTo(5L)
            assertThat(result).isSameAs(membership)
        }

        @Test
        fun `throws when the corrected interval is invalid`() {
            withViolation()

            assertThatThrownBy {
                useCases.correct(
                    id = 3L,
                    userId = 2L,
                    memberType = null,
                    startDate = LocalDate.of(2025, 1, 1),
                    endDate = null,
                    incasso = null,
                    version = 5L,
                )
            }.isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).update(any())
        }
    }

    @Nested
    inner class End {

        @Test
        fun `ends membership by setting endDate to today`() {
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = null
            )
            whenever(membershipService.findById(4L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)
            noViolations()

            assertThat(useCases.end(4L).endDate).isEqualTo(LocalDate.now())
            verify(membershipService).update(membership)
        }

        @Test
        fun `throws when the resulting interval is invalid`() {
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.now(),
                endDate = null
            )
            whenever(membershipService.findById(5L)).thenReturn(membership)
            withViolation()

            assertThatThrownBy { useCases.end(5L) }
                .isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).update(any())
        }
    }

    @Nested
    inner class Reopen {

        @Test
        fun `reopens membership by clearing endDate`() {
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 1, 1)
            )
            whenever(membershipService.findById(6L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)
            noViolations()

            assertThat(useCases.reopen(6L).endDate).isNull()
            verify(membershipService).update(membership)
        }

        @Test
        fun `throws when reopening would conflict`() {
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 1, 1)
            ).apply { id = 6L }
            whenever(membershipService.findById(6L)).thenReturn(membership)
            withViolation()

            assertThatThrownBy { useCases.reopen(6L) }
                .isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).update(any())
        }
    }

    @Nested
    inner class FindById {

        @Test
        fun `returns membership by id`() {
            val expected = Membership(user = testUser("found"), startDate = LocalDate.now())
            whenever(membershipService.findById(7L)).thenReturn(expected)

            assertThat(useCases.findById(7L)).isSameAs(expected)
            verify(membershipService).findById(7L)
        }
    }

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
