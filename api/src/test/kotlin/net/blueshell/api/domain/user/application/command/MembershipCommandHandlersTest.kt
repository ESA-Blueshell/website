package net.blueshell.api.domain.user.application.command

import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.application.query.MembershipQuery
import net.blueshell.api.domain.user.command.BoardCreateMembershipCommand
import net.blueshell.api.domain.user.command.CreateMembershipCommand
import net.blueshell.api.domain.user.command.FindMembershipByIdCommand
import net.blueshell.api.domain.user.command.FindMembershipsCommand
import net.blueshell.api.domain.user.command.UpdateMembershipCommand
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.access.AccessDeniedException
import java.time.LocalDate

class MembershipCommandHandlersTest {

    private val membershipService = mock<MembershipService>()
    private val userService = mock<UserService>()

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

        private val handler = CreateMembershipHandler(membershipService, userService)

        @Test
        fun `creates membership when user is eligible`() {
            val user = testUser("john")
            whenever(userService.findById(1L)).thenReturn(user)

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

        private val handler = BoardCreateMembershipHandler(membershipService, userService)

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
    inner class UpdateMembership {

        private val handler = UpdateMembershipHandler(membershipService, userService)

        @Test
        fun `updates membership fields and version`() {
            val user = testUser("john")
            val membership = Membership(
                user = user,
                startDate = LocalDate.of(2024, 1, 1),
                memberType = MemberType.ALUMNI,
                endDate = null,
                incasso = false,
            ).apply { version = 1L }
            whenever(membershipService.findById(3L)).thenReturn(membership)
            whenever(userService.findById(2L)).thenReturn(user)
            whenever(membershipService.update(membership)).thenReturn(membership)

            val result = handler.handle(
                UpdateMembershipCommand(
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
