package net.blueshell.api.domain.user.application.command

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CorrectMembershipCommand
import net.blueshell.api.domain.auth.application.SignupCompletionService
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.domain.user.application.MemberProfileService
import net.blueshell.api.domain.user.command.SubmitMembershipApplicationCommand
import net.blueshell.api.domain.user.persistence.MemberProfile
import net.blueshell.api.domain.user.command.EndMembershipCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.ReopenMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDate

class MembershipCommandHandlersTest {

    private val membershipService = mock<MembershipService>()
    private val userService = mock<UserService>()
    private val validator = mock<Validator>()

    private fun noViolations() {
        whenever(validator.validate(any<CorrectMembershipCommand>())).thenReturn(mutableSetOf())
    }

    private fun withViolation() {
        whenever(validator.validate(any<CorrectMembershipCommand>()))
            .thenReturn(mutableSetOf(mock<ConstraintViolation<CorrectMembershipCommand>>()))
    }

    @Nested
    inner class FindMemberships {

        private val handler = FindMembershipsHandler(membershipService)

        @Test
        fun `returns memberships by query`() {
            val query = MembershipQuery(from = LocalDate.of(2025, 1, 1))
            val expected = mutableListOf(Membership(user = testUser("expected"), startDate = LocalDate.now()))
            whenever(membershipService.findByQuery(query)).thenReturn(expected)

            val result = handler.handle(FindMembershipsCommand(query))

            assertThat(result).isSameAs(expected)
            verify(membershipService).findByQuery(query)
        }
    }

    @Nested
    inner class SubmitMembershipApplication {

        private val memberProfiles = mock<MemberProfileService>()
        private val completion = mock<SignupCompletionService>()
        private val handler = SubmitMembershipApplicationHandler(userService, memberProfiles, completion)

        private fun applicantWithProfile(): MemberProfile {
            val user = testUser("john")
            val profile = MemberProfile(user = user, bhv = false, ehbo = false)
            user.replaceMemberProfile(profile)
            whenever(userService.findById(1L)).thenReturn(user)
            return profile
        }

        @Test
        fun `stamps the acceptance and returns the outcome`() {
            val profile = applicantWithProfile()
            whenever(completion.completeIfReady(1L))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

            val outcome = handler.handle(SubmitMembershipApplicationCommand(1L, conditionsAccepted = true))

            assertThat(profile.conditionsAcceptedAt).isNotNull()
            verify(memberProfiles).update(profile)
            assertThat(outcome.membershipStarted).isTrue()
        }

        @Test
        fun `refuses an application the completion rule cannot commit`() {
            applicantWithProfile()
            whenever(completion.completeIfReady(1L))
                .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = false))

            assertThatThrownBy {
                handler.handle(SubmitMembershipApplicationCommand(1L, conditionsAccepted = true))
            }.isInstanceOf(AccessDeniedException::class.java)
        }

        @Test
        fun `refuses an account with no member profile`() {
            whenever(userService.findById(1L)).thenReturn(testUser("john"))

            assertThatThrownBy {
                handler.handle(SubmitMembershipApplicationCommand(1L, conditionsAccepted = true))
            }.isInstanceOf(AccessDeniedException::class.java)
                .hasMessageContaining("Complete profile is required")
        }
    }

    @Nested
    inner class BoardCreateMembership {

        private val handler = BoardCreateMembershipHandler(membershipService, userService)

        @Test
        fun `creates membership from board command fields`() {
            val user = testUser("john")
            whenever(userService.findById(2L)).thenReturn(user)
            val startDate = LocalDate.of(2025, 1, 1)
            val endDate = LocalDate.of(2025, 12, 31)
            val expected = Membership(user = user, startDate = startDate)
            whenever(membershipService.create(any())).thenReturn(expected)

            val result = handler.handle(
                BoardCreateMembershipCommand(
                    userId = 2L,
                    memberType = MemberType.REGULAR,
                    startDate = startDate,
                    endDate = endDate,
                    incasso = true
                )
            )

            assertThat(result).isSameAs(expected)
            verify(membershipService).create(org.mockito.kotlin.check {
                assertThat(it.user).isSameAs(user)
                assertThat(it.memberType).isEqualTo(MemberType.REGULAR)
                assertThat(it.startDate).isEqualTo(startDate)
                assertThat(it.endDate).isEqualTo(endDate)
                assertThat(it.incasso).isTrue()
            })
        }
    }

    @Nested
    inner class CorrectMembership {

        private val handler = CorrectMembershipHandler(membershipService)

        @Test
        fun `corrects membership fields and version`() {
            val user = testUser("john")
            val membership = Membership(
                user = user,
                startDate = LocalDate.of(2024, 1, 1),
                memberType = MemberType.ALUMNI,
                endDate = null,
                incasso = false,
            ).apply { version = 1L }
            whenever(membershipService.findById(3L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)

            val result = handler.handle(
                CorrectMembershipCommand(
                    id = 3L,
                    userId = 2L,
                    memberType = MemberType.HONORARY,
                    startDate = LocalDate.of(2025, 1, 1),
                    endDate = LocalDate.of(2025, 12, 31),
                    incasso = true,
                    version = 5L
                )
            )

            assertThat(membership.memberType).isEqualTo(MemberType.HONORARY)
            assertThat(membership.startDate).isEqualTo(LocalDate.of(2025, 1, 1))
            assertThat(membership.endDate).isEqualTo(LocalDate.of(2025, 12, 31))
            assertThat(membership.incasso).isTrue()
            assertThat(membership.version).isEqualTo(5L)
            assertThat(result).isSameAs(membership)
        }
    }

    @Nested
    inner class EndMembership {

        private val handler = EndMembershipHandler(membershipService, validator)

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

            val result = handler.handle(EndMembershipCommand(4L))

            assertThat(result.endDate).isEqualTo(LocalDate.now())
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

            assertThatThrownBy {
                handler.handle(EndMembershipCommand(5L))
            }.isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).update(any())
        }
    }

    @Nested
    inner class ReopenMembership {

        private val handler = ReopenMembershipHandler(membershipService, validator)

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

            val result = handler.handle(ReopenMembershipCommand(6L))

            assertThat(result.endDate).isNull()
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

            assertThatThrownBy {
                handler.handle(ReopenMembershipCommand(6L))
            }.isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).update(any())
        }
    }

    @Nested
    inner class FindMembershipById {

        private val handler = FindMembershipByIdHandler(membershipService)

        @Test
        fun `returns membership by id`() {
            val expected = Membership(user = testUser("found"), startDate = LocalDate.now())
            whenever(membershipService.findById(7L)).thenReturn(expected)

            val result = handler.handle(FindMembershipByIdCommand(7L))

            assertThat(result).isSameAs(expected)
            verify(membershipService).findById(7L)
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
