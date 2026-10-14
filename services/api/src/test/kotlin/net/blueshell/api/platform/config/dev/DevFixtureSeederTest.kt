package net.blueshell.api.platform.config.dev

import net.blueshell.api.auth.domain.UserActivationService
import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.PaymentDirectory
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.security.crypto.password.PasswordEncoder

class DevFixtureSeederTest {
    private val users: UserService = mock()
    private val memberships: MembershipService = mock()
    private val periods: ContributionPeriodService = mock()
    private val payments: PaymentDirectory = mock()
    private val encoder: PasswordEncoder = mock()
    private val seeder =
        DevFixtureSeeder(users, memberships, periods, mock<ContributionService>(), mock<UserActivationService>(), encoder, payments)

    @Test
    fun `a seeded account on incasso pays by incasso, on the person rather than the membership`() {
        var nextId = 100L
        whenever(encoder.encode(any())).thenReturn("hashed")
        whenever(users.create(any())).thenAnswer { (it.arguments[0] as User).also { user -> user.id = nextId++ } }
        whenever(memberships.create(any())).thenAnswer { it.arguments[0] as Membership }
        whenever(periods.create(any())).thenAnswer { it.arguments[0] }

        seeder.seed()

        val onIncasso = DevFixtures.ACCOUNTS.count { it.membership?.incasso == true }
        verify(payments, times(onIncasso)).payBy(any(), eq(true))
        verify(payments, never()).payBy(any(), eq(false))
    }
}
