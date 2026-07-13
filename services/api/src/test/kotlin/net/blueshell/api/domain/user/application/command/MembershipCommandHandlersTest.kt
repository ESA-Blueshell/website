package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.exception.InvalidMembershipException
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.application.validation.MembershipInvariants
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CorrectMembershipCommand
import net.blueshell.api.domain.user.command.CreateMembershipCommand
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
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.http.HttpStatus
import org.springframework.security.access.AccessDeniedException
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

class MembershipCommandHandlersTest {

    private val membershipService = mock<MembershipService>()
    private val userService = mock<UserService>()
    private val invariants = mock<MembershipInvariants>()

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
    inner class CreateMembership {

        private val handler = CreateMembershipHandler(membershipService, userService, invariants)

        @Test
        fun `creates membership when user is eligible`() {
            val user = testUser("john")
            whenever(userService.findById(1L)).thenReturn(user)
            whenever(membershipService.create(org.mockito.kotlin.any())).thenAnswer { it.getArgument<Membership>(0) }

            val result = handler.handle(
                CreateMembershipCommand(
                    userId = 1L,
                    isMember = false,
                    hasAddress = true,
                    hasMemberProfile = true
                )
            )

            verify(membershipService).create(result)
            assertThat(result.user).isSameAs(user)
        }

        @Test
        fun `throws when user already has active membership`() {
            assertThatThrownBy {
                handler.handle(
                    CreateMembershipCommand(
                        userId = 1L,
                        isMember = true,
                        hasAddress = true,
                        hasMemberProfile = true
                    )
                )
            }.isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("User already has an active membership")
        }

        @Test
        fun `throws when user has no address`() {
            assertThatThrownBy {
                handler.handle(
                    CreateMembershipCommand(
                        userId = 1L,
                        isMember = false,
                        hasAddress = false,
                        hasMemberProfile = true
                    )
                )
            }.isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("User must have an address")
        }

        @Test
        fun `throws when user has no member profile`() {
            assertThatThrownBy {
                handler.handle(
                    CreateMembershipCommand(
                        userId = 1L,
                        isMember = false,
                        hasAddress = true,
                        hasMemberProfile = false
                    )
                )
            }.isInstanceOf(AccessDeniedException::class.java)
                .hasMessage("Complete profile is required before applying for membership")
        }
    }

    @Nested
    inner class BoardCreateMembership {

        private val handler = BoardCreateMembershipHandler(membershipService, userService, invariants)

        @Test
        fun `creates membership from board command fields`() {
            val user = testUser("john")
            whenever(userService.findById(2L)).thenReturn(user)
            val startDate = LocalDate.of(2025, 1, 1)
            val endDate = LocalDate.of(2025, 12, 31)
            val expected = Membership(user = user, startDate = startDate)
            whenever(membershipService.create(org.mockito.kotlin.any())).thenReturn(expected)

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

        private val handler = CorrectMembershipHandler(membershipService, invariants)

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

            assertThat(membership.user).isSameAs(user)
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

        private val handler = EndMembershipHandler(membershipService)

        @Test
        fun `ends membership by setting endDate to today`() {
            val user = testUser("john")
            val membership = Membership(
                user = user,
                startDate = LocalDate.of(2024, 1, 1),
                endDate = null
            )
            whenever(membershipService.findById(4L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)

            val result = handler.handle(EndMembershipCommand(4L))

            assertThat(result.endDate).isEqualTo(LocalDate.now())
            verify(membershipService).update(membership)
        }

        @Test
        fun `rejects ending membership that started today`() {
            val user = testUser("john")
            val membership = Membership(
                user = user,
                startDate = LocalDate.now(),
                endDate = null
            )
            whenever(membershipService.findById(5L)).thenReturn(membership)

            assertThatThrownBy {
                handler.handle(EndMembershipCommand(5L))
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(ex.reason).contains("delete it instead")
            }
        }

        @Test
        fun `rejects ending an already-ended membership`() {
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2024, 6, 1)
            )
            whenever(membershipService.findById(8L)).thenReturn(membership)

            assertThatThrownBy {
                handler.handle(EndMembershipCommand(8L))
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(ex.reason).contains("already ended")
            }
        }
    }

    @Nested
    inner class ReopenMembership {

        private val handler = ReopenMembershipHandler(membershipService, invariants)

        @Test
        fun `reopens membership by clearing endDate`() {
            val user = testUser("john")
            val membership = Membership(
                user = user,
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 1, 1)
            )
            whenever(membershipService.findById(6L)).thenReturn(membership)
            whenever(membershipService.update(membership)).thenReturn(membership)

            val result = handler.handle(ReopenMembershipCommand(6L))

            assertThat(result.endDate).isNull()
            verify(membershipService).update(membership)
        }

        @Test
        fun `rejects reopening when another active membership exists`() {
            val user = testUser("john")
            val membership = Membership(
                user = user,
                startDate = LocalDate.of(2024, 1, 1),
                endDate = LocalDate.of(2025, 1, 1)
            ).apply { id = 6L }
            whenever(membershipService.findById(6L)).thenReturn(membership)
            org.mockito.kotlin.doThrow(
                InvalidMembershipException("User already has an active membership")
            ).whenever(invariants).validate(
                org.mockito.kotlin.any(),
                org.mockito.kotlin.anyOrNull(),
                org.mockito.kotlin.any(),
                org.mockito.kotlin.anyOrNull()
            )

            assertThatThrownBy {
                handler.handle(ReopenMembershipCommand(6L))
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(ex.reason).contains("active membership")
            }
        }

        @Test
        fun `rejects reopening an already-active membership`() {
            val membership = Membership(
                user = testUser("john"),
                startDate = LocalDate.of(2024, 1, 1),
                endDate = null
            )
            whenever(membershipService.findById(9L)).thenReturn(membership)

            assertThatThrownBy {
                handler.handle(ReopenMembershipCommand(9L))
            }.isInstanceOfSatisfying(ResponseStatusException::class.java) { ex ->
                assertThat(ex.statusCode).isEqualTo(HttpStatus.BAD_REQUEST)
                assertThat(ex.reason).contains("already active")
            }
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
