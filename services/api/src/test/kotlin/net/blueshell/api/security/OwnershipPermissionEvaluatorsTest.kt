package net.blueshell.api.security

import net.blueshell.api.domain.user.application.permission.AddressPermission
import net.blueshell.api.domain.contribution.application.permission.ContributionPermission
import net.blueshell.api.domain.user.application.permission.MembershipPermission
import net.blueshell.api.domain.user.application.permission.UserPermission

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.Contribution
import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class OwnershipPermissionEvaluatorsTest {

    @Nested
    inner class AddressPermissionEvaluator {
        private val service = mock<AddressService>()
        private val evaluator = AddressPermission(service)
        private val target = mock<Address>()

        @Test
        fun `denies null authentication or permission`() {
            assertThat(evaluator.hasPermission(null, target, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), target, null)).isFalse()
        }

        @Test
        fun `null entity path is board only for read write delete`() {
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `entity read and write are allowed for board or matching address owner`() {
            whenever(target.id).thenReturn(55L)
            val owner = guestAuth(id = 12L, addressId = 55L)
            val other = guestAuth(id = 13L, addressId = 99L)

            assertThat(evaluator.hasPermission(owner, target, "read")).isTrue()
            assertThat(evaluator.hasPermission(owner, target, "write")).isTrue()
            assertThat(evaluator.hasPermission(other, target, "read")).isFalse()
            assertThat(evaluator.hasPermission(other, target, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), target, "delete")).isTrue()
            assertThat(evaluator.hasPermission(owner, target, "delete")).isFalse()
        }

        @Test
        fun `hasPermissionId handles null id fallback and id lookup`() {
            whenever(target.id).thenReturn(10L)
            whenever(service.findById(10L)).thenReturn(target)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermissionId(boardAuth(), 10L, "read")).isTrue()
            verify(service).findById(10L)
        }
    }

    @Nested
    inner class MembershipPermissionEvaluator {
        private val service = mock<MembershipService>()
        private val evaluator = MembershipPermission(service)
        private val membership = mock<Membership>()

        @Test
        fun `null entity path is board only`() {
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `entity read allows owner while write delete stay board only`() {
            whenever(membership.userId).thenReturn(12L)
            val owner = guestAuth(id = 12L)
            val other = guestAuth(id = 13L)

            assertThat(evaluator.hasPermission(owner, membership, "read")).isTrue()
            assertThat(evaluator.hasPermission(other, membership, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), membership, "write")).isTrue()
            assertThat(evaluator.hasPermission(owner, membership, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), membership, "delete")).isTrue()
            assertThat(evaluator.hasPermission(owner, membership, "delete")).isFalse()
        }

        @Test
        fun `hasPermissionId supports null id fallback and loaded membership path`() {
            whenever(membership.userId).thenReturn(22L)
            whenever(service.findById(22L)).thenReturn(membership)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermissionId(boardAuth(), 22L, "read")).isTrue()
            verify(service).findById(22L)
        }
    }

    @Nested
    inner class ContributionPermissionEvaluator {
        private val service = mock<ContributionService>()
        private val evaluator = ContributionPermission(service)
        private val contribution = mock<Contribution>()

        @Test
        fun `null entity path is board only for all operations`() {
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "write")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `entity read allows board or owner while write delete remain board only`() {
            whenever(contribution.userId).thenReturn(32L)
            val owner = guestAuth(id = 32L)

            assertThat(evaluator.hasPermission(owner, contribution, "read")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(id = 9L), contribution, "read")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), contribution, "read")).isTrue()
            assertThat(evaluator.hasPermission(owner, contribution, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), contribution, "write")).isTrue()
            assertThat(evaluator.hasPermission(owner, contribution, "delete")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), contribution, "delete")).isTrue()
        }

        @Test
        fun `hasPermissionId requires a non-null contribution id and resolves service path`() {
            val id = Contribution.Id(userId = 32L, contributionPeriodId = 5L)
            whenever(contribution.userId).thenReturn(32L)
            whenever(service.findById(id)).thenReturn(contribution)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermissionId(boardAuth(), id, "delete")).isTrue()
            verify(service).findById(id)
        }
    }

    @Nested
    inner class UserPermissionEvaluator {
        private val service = mock<UserService>()
        private val evaluator = UserPermission(service)
        private val target = mock<User>()

        @Test
        fun `null entity path allows board for read delete and admin for roles`() {
            assertThat(evaluator.hasPermission(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "delete")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), null, "roles")).isFalse()
            assertThat(evaluator.hasPermission(adminAuth(), null, "roles")).isTrue()
            assertThat(evaluator.hasPermission(guestAuth(), null, "read")).isFalse()
            assertThat(evaluator.hasPermission(guestAuth(), null, "unknown")).isFalse()
        }

        @Test
        fun `entity path combines ownership board and admin checks`() {
            whenever(target.id).thenReturn(7L)
            val owner = guestAuth(id = 7L)
            val other = guestAuth(id = 8L)

            assertThat(evaluator.hasPermission(owner, target, "read")).isTrue()
            assertThat(evaluator.hasPermission(owner, target, "write")).isTrue()
            assertThat(evaluator.hasPermission(other, target, "read")).isFalse()
            assertThat(evaluator.hasPermission(other, target, "write")).isFalse()
            assertThat(evaluator.hasPermission(boardAuth(), target, "read")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), target, "delete")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), target, "email")).isTrue()
            assertThat(evaluator.hasPermission(owner, target, "email")).isFalse()
            assertThat(evaluator.hasPermission(adminAuth(), target, "roles")).isTrue()
            assertThat(evaluator.hasPermission(boardAuth(), target, "roles")).isFalse()
        }

        @Test
        fun `hasPermissionId falls back for null id and resolves existing user by id`() {
            whenever(target.id).thenReturn(7L)
            whenever(service.findById(7L)).thenReturn(target)

            assertThat(evaluator.hasPermissionId(boardAuth(), null, "read")).isTrue()
            assertThat(evaluator.hasPermissionId(boardAuth(), 7L, "delete")).isTrue()
            verify(service).findById(7L)
        }
    }
}
