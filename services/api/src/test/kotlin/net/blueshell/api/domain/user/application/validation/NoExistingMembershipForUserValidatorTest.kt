package net.blueshell.api.domain.user.application.validation

import net.blueshell.api.domain.user.application.MembershipService
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NoExistingMembershipForUserValidatorTest {

    private val memberships = mock<MembershipService>()
    private val validator = NoExistingMembershipForUserValidator(memberships)

    @Test
    fun `accepts null candidate or null user id`() {
        Assertions.assertThat(validator.isValid(null, mock())).isTrue()

        val candidate = object : MembershipUserIdCandidate {
            override val membershipUserId: Long? = null
        }
        Assertions.assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `accepts when user has no active membership`() {
        whenever(memberships.existsActiveMembershipByUserId(1)).thenReturn(false)

        val candidate = object : MembershipUserIdCandidate {
            override val membershipUserId: Long? = 1
        }

        Assertions.assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects when active membership already exists`() {
        whenever(memberships.existsActiveMembershipByUserId(2)).thenReturn(true)

        val candidate = object : MembershipUserIdCandidate {
            override val membershipUserId: Long? = 2
        }

        Assertions.assertThat(validator.isValid(candidate, mock())).isFalse()
    }
}