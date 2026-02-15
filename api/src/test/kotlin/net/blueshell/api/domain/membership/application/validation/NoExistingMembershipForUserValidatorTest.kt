package net.blueshell.api.domain.membership.application.validation

import net.blueshell.api.domain.membership.application.MembershipService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NoExistingMembershipForUserValidatorTest {

    private val memberships = mock<MembershipService>()
    private val validator = NoExistingMembershipForUserValidator(memberships)

    @Test
    fun `accepts null candidate or null user id`() {
        assertThat(validator.isValid(null, mock())).isTrue()

        val candidate = object : MembershipUserIdCandidate {
            override val membershipUserId: Long? = null
        }
        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `accepts when user has no membership`() {
        whenever(memberships.existsByUserId(1)).thenReturn(false)

        val candidate = object : MembershipUserIdCandidate {
            override val membershipUserId: Long? = 1
        }

        assertThat(validator.isValid(candidate, mock())).isTrue()
    }

    @Test
    fun `rejects when membership already exists`() {
        whenever(memberships.existsByUserId(2)).thenReturn(true)

        val candidate = object : MembershipUserIdCandidate {
            override val membershipUserId: Long? = 2
        }

        assertThat(validator.isValid(candidate, mock())).isFalse()
    }
}
