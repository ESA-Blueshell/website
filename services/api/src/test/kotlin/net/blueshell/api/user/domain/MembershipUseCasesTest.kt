package net.blueshell.api.user.domain

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import net.blueshell.api.auth.domain.SignupCompletionService
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.user.api.MemberProfileService
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.MembershipType
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.check
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.dao.OptimisticLockingFailureException
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class MembershipUseCasesTest {
    private val membershipService = mock<MembershipService>()
    private val userService = mock<UserService>()
    private val memberProfiles = mock<MemberProfileService>()
    private val completion = mock<SignupCompletionService>()
    private val validator = mock<Validator>()

    private val useCases =
        MembershipUseCases(
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

            val outcome = useCases.apply(1L, conditionsAccepted = true)

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

            assertThatThrownBy { useCases.apply(1L, conditionsAccepted = true) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Membership application is not complete")
                .asInstanceOf(
                    org.assertj.core.api.InstanceOfAssertFactories
                        .type(ResponseStatusException::class.java),
                ).extracting { it.statusCode }
                .isEqualTo(HttpStatus.FORBIDDEN)
        }

        @Test
        fun `refuses an account with no member profile`() {
            noViolations()
            whenever(userService.findById(1L)).thenReturn(testUser("john"))

            // The sentence has to survive the trip: a security exception is translated
            // outside the dispatch and the applicant sees no reason at all.
            assertThatThrownBy { useCases.apply(1L, conditionsAccepted = true) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("Complete profile is required")
        }

        @Test
        fun `refuses an application that does not accept the conditions and records nothing`() {
            val profile = applicantWithProfile()

            assertThatThrownBy { useCases.apply(1L, conditionsAccepted = false) }
                .isInstanceOf(ResponseStatusException::class.java)
                .hasMessageContaining("The membership conditions must be accepted")
                .asInstanceOf(
                    org.assertj.core.api.InstanceOfAssertFactories
                        .type(ResponseStatusException::class.java),
                ).extracting { it.statusCode }
                .isEqualTo(HttpStatus.BAD_REQUEST)
            assertThat(profile.conditionsAcceptedAt).isNull()
            verifyNoInteractions(memberProfiles, completion)
        }

        @Test
        fun `refuses an application that would overlap an existing membership`() {
            withViolation()

            assertThatThrownBy { useCases.apply(1L, conditionsAccepted = true) }
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

            val result =
                useCases.boardCreate(
                    userId = 2L,
                    memberType = MemberType.REGULAR,
                    startDate = startDate,
                    endDate = endDate,
                )

            assertThat(result).isSameAs(expected)
            verify(membershipService).create(
                check {
                    assertThat(it.user).isSameAs(user)
                    assertThat(it.memberType).isEqualTo(MemberType.REGULAR)
                    assertThat(it.startDate).isEqualTo(startDate)
                    assertThat(it.endDate).isEqualTo(endDate)
                },
            )
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
                )
            }.isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).create(any())
        }
    }

    @Nested
    inner class Correct {
        @Test
        fun `corrects membership dates, keeping the version it was read at and the type it holds`() {
            noViolations()
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.of(2024, 1, 1),
                    memberType = MemberType.ALUMNI,
                    endDate = null,
                ).apply { version = 1L }
            whenever(membershipService.findById(3L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)

            val result =
                useCases.correct(
                    id = 3L,
                    userId = 2L,
                    startDate = LocalDate.of(2025, 1, 1),
                    endDate = LocalDate.of(2025, 12, 31),
                    version = 1L,
                )

            assertThat(membership.memberType).isEqualTo(MemberType.ALUMNI)
            assertThat(membership.startDate).isEqualTo(LocalDate.of(2025, 1, 1))
            assertThat(membership.endDate).isEqualTo(LocalDate.of(2025, 12, 31))
            assertThat(membership.version).isEqualTo(1L)
            assertThat(result).isSameAs(membership)
        }

        @Test
        fun `refuses a correction made against an older version, before checking the interval`() {
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.of(2024, 1, 1),
                    memberType = MemberType.ALUMNI,
                    endDate = null,
                ).apply { version = 2L }
            whenever(membershipService.findById(3L)).thenReturn(membership)

            assertThatThrownBy {
                useCases.correct(
                    id = 3L,
                    userId = 2L,
                    startDate = LocalDate.of(2025, 1, 1),
                    endDate = LocalDate.of(2025, 12, 31),
                    version = 1L,
                )
            }.isInstanceOf(OptimisticLockingFailureException::class.java)
            assertThat(membership.memberType).isEqualTo(MemberType.ALUMNI)
            verify(validator, never()).validate(any<Any>())
            verify(membershipService, never()).update(any())
        }

        @Test
        fun `throws when the corrected interval is invalid`() {
            withViolation()
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.of(2024, 1, 1),
                    memberType = MemberType.ALUMNI,
                    endDate = null,
                ).apply { version = 5L }
            whenever(membershipService.findById(3L)).thenReturn(membership)

            assertThatThrownBy {
                useCases.correct(
                    id = 3L,
                    userId = 2L,
                    startDate = LocalDate.of(2025, 1, 1),
                    endDate = null,
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
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.of(2024, 1, 1),
                    endDate = null,
                )
            whenever(membershipService.findById(4L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)
            noViolations()

            assertThat(useCases.end(4L).endDate).isEqualTo(LocalDate.now())
            verify(membershipService).update(membership)
        }

        @Test
        fun `throws when the resulting interval is invalid`() {
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.now(),
                    endDate = null,
                )
            whenever(membershipService.findById(5L)).thenReturn(membership)
            withViolation()

            assertThatThrownBy { useCases.end(5L) }
                .isInstanceOf(ConstraintViolationException::class.java)
            verify(membershipService, never()).update(any())
        }
    }

    @Nested
    inner class Types {
        private val start = LocalDate.of(2022, 9, 1)
        private val alumniSince = LocalDate.of(2025, 9, 1)

        private fun held(endDate: LocalDate? = null): Membership =
            Membership(user = testUser("john"), startDate = start, endDate = endDate).apply {
                addType(MembershipType(this, MemberType.REGULAR, start))
                addType(MembershipType(this, MemberType.ALUMNI, alumniSince))
                types.forEachIndexed { at, type -> type.id = at + 1L }
                whenever(membershipService.findById(9L)).thenReturn(this)
                whenever(membershipService.update(this)).thenReturn(this)
            }

        @Test
        fun `records a type on a free day while the membership runs`() {
            val membership = held()

            useCases.recordType(9L, MemberType.HONORARY, LocalDate.of(2026, 1, 1))

            assertThat(membership.types.map { it.type }).containsExactly(MemberType.REGULAR, MemberType.ALUMNI, MemberType.HONORARY)
            assertThat(membership.memberType).isEqualTo(MemberType.HONORARY)
        }

        @Test
        fun `refuses a type before the start, or on or after the end`() {
            held(endDate = LocalDate.of(2026, 1, 1))

            assertThatThrownBy { useCases.recordType(9L, MemberType.HONORARY, start.minusDays(1)) }
                .isInstanceOf(MembershipTypeOutsideMembership::class.java)
            assertThatThrownBy { useCases.recordType(9L, MemberType.HONORARY, LocalDate.of(2026, 1, 1)) }
                .isInstanceOf(MembershipTypeOutsideMembership::class.java)
            verify(membershipService, never()).update(any())
        }

        @Test
        fun `a type recorded on a day another starts replaces it, the first included`() {
            val membership = held()

            useCases.recordType(9L, MemberType.HONORARY, alumniSince)
            useCases.recordType(9L, MemberType.ALUMNI, start)

            assertThat(membership.types.map { it.type to it.since })
                .containsExactly(MemberType.ALUMNI to start, MemberType.HONORARY to alumniSince)
            assertThat(membership.memberType).isEqualTo(MemberType.HONORARY)
        }

        @Test
        fun `a pending membership turned honorary is active from that day, and an active one keeps its day`() {
            val pending = held()
            val honorarySince = LocalDate.of(2026, 1, 1)

            useCases.recordType(9L, MemberType.HONORARY, honorarySince)
            assertThat(pending.activatedOn).isEqualTo(honorarySince)

            useCases.recordType(9L, MemberType.HONORARY, alumniSince)
            assertThat(pending.activatedOn).isEqualTo(honorarySince)
        }

        @Test
        fun `removes a later type, never the first`() {
            val membership = held()

            assertThatThrownBy { useCases.removeType(9L, 1L) }.isInstanceOf(FirstMembershipTypeStartsTheMembership::class.java)
            assertThatThrownBy { useCases.removeType(9L, 7L) }.isInstanceOf(MembershipTypeNotFound::class.java)
            useCases.removeType(9L, 2L)

            assertThat(membership.types.map { it.type }).containsExactly(MemberType.REGULAR)
            assertThat(membership.memberType).isEqualTo(MemberType.REGULAR)
        }

        @Test
        fun `moving the start moves the first type, and a later type must stay inside`() {
            noViolations()
            val membership = held().apply { version = 1L }

            useCases.correct(9L, 2L, start.plusDays(10), null, version = 1L)
            assertThat(membership.types.first().since).isEqualTo(start.plusDays(10))

            assertThatThrownBy { useCases.correct(9L, 2L, alumniSince, null, version = 1L) }
                .isInstanceOf(MembershipTypeOutsideMembership::class.java)
            assertThatThrownBy { useCases.correct(9L, 2L, start, alumniSince, version = 1L) }
                .isInstanceOf(MembershipTypeOutsideMembership::class.java)
        }

        @Test
        fun `a membership cannot end on or before a type it holds`() {
            noViolations()
            held()

            assertThatThrownBy { useCases.end(9L, alumniSince) }.isInstanceOf(MembershipTypeOutsideMembership::class.java)
        }
    }

    @Nested
    inner class Reopen {
        @Test
        fun `reopens membership by clearing endDate`() {
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.of(2024, 1, 1),
                    endDate = LocalDate.of(2025, 1, 1),
                )
            whenever(membershipService.findById(6L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)
            noViolations()

            assertThat(useCases.reopen(6L).endDate).isNull()
            verify(membershipService).update(membership)
        }

        @Test
        fun `throws when reopening would conflict`() {
            val membership =
                Membership(
                    user = testUser("john"),
                    startDate = LocalDate.of(2024, 1, 1),
                    endDate = LocalDate.of(2025, 1, 1),
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

    private fun testUser(username: String) =
        User(
            username = username,
            email = "$username@example.com",
            password = "encoded",
            initials = "JD",
            firstName = "John",
            prefix = null,
            lastName = "Doe",
            phoneNumber = "0612345678",
            discord = "john#0001",
            newsletter = true,
        )
}
