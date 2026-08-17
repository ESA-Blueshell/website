package net.blueshell.api.domain.contribution.application.command

import net.blueshell.api.domain.contribution.application.ContributionService
import net.blueshell.api.domain.contribution.persistence.ContributionPeriod
import net.blueshell.api.domain.user.application.MembershipService
import net.blueshell.api.domain.user.application.UserService
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkFeeType
import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class DecideReminderTest {

    private val users = mock<UserService>()
    private val memberships = mock<MembershipService>()
    private val contributions = mock<ContributionService>()

    private val period = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearFee = 20.0,
        fullYearFee = 40.0,
        alumniFee = 10.0,
    )
    private val cutoff = LocalDate.of(2024, 7, 1)

    @Test
    fun `a regular member with an unpaid contribution is included at the full year fee`() {
        stub(memberType = MemberType.REGULAR, start = LocalDate.of(2024, 2, 1), paid = false)

        val decision = decide()

        assertThat(decision.disposition).isEqualTo(BulkRowDisposition.INCLUDED)
        assertThat(decision.reason).isNull()
        assertThat(decision.recommendedFeeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
        assertThat(decision.amount).isEqualTo(40.0)
    }

    @Test
    fun `the fee tier follows the latest membership, not the earliest`() {
        // This is why reminder rows cannot be decided in the browser: the frontend
        // derives the earliest start, which would bill the full year here.
        stubUser(paid = false)
        whenever(memberships.findByUserId(USER_ID)).thenReturn(
            mutableListOf(
                membership(MemberType.REGULAR, LocalDate.of(2024, 1, 1)),
                membership(MemberType.REGULAR, LocalDate.of(2024, 9, 1)),
            )
        )

        val decision = decide()

        assertThat(decision.recommendedFeeType).isEqualTo(BulkFeeType.HALF_YEAR_FEE)
        assertThat(decision.amount).isEqualTo(20.0)
        assertThat(decision.memberSince).isEqualTo(LocalDate.of(2024, 9, 1))
    }

    @Test
    fun `a membership starting on the cutoff pays the full year`() {
        stub(memberType = MemberType.REGULAR, start = cutoff, paid = false)

        assertThat(decide().recommendedFeeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
    }

    @Test
    fun `an honorary member is excluded and cannot be re-included`() {
        stub(memberType = MemberType.HONORARY, start = LocalDate.of(2024, 2, 1), paid = false)

        val decision = decide()

        assertThat(decision.disposition).isEqualTo(BulkRowDisposition.EXCLUDED)
        assertThat(decision.reason).isEqualTo(BulkRowReason.HONORARY)
        assertThat(decision.amount).isNull()
    }

    @Test
    fun `a blank email is skipped rather than silently dropped at send time`() {
        stub(memberType = MemberType.REGULAR, start = LocalDate.of(2024, 2, 1), paid = false, email = "")

        val decision = decide()

        assertThat(decision.disposition).isEqualTo(BulkRowDisposition.SKIPPED)
        assertThat(decision.reason).isEqualTo(BulkRowReason.NO_EMAIL)
        assertThat(decision.emailMissing).isTrue()
    }

    @Test
    fun `an already paid member is a warning the operator may override`() {
        stub(memberType = MemberType.REGULAR, start = LocalDate.of(2024, 2, 1), paid = true)

        val decision = decide()

        assertThat(decision.disposition).isEqualTo(BulkRowDisposition.WARNING)
        assertThat(decision.reason).isEqualTo(BulkRowReason.ALREADY_PAID)
    }

    @Test
    fun `a member with no membership at all is treated as regular`() {
        stubUser(paid = false)
        whenever(memberships.findByUserId(USER_ID)).thenReturn(mutableListOf())

        val decision = decide()

        assertThat(decision.memberType).isEqualTo(MemberType.REGULAR)
        assertThat(decision.memberSince).isNull()
        assertThat(decision.recommendedFeeType).isEqualTo(BulkFeeType.FULL_YEAR_FEE)
    }

    private fun decide(): EmailBulkDecision =
        decideReminder(USER_ID, PERIOD_ID, period, cutoff, users, memberships, contributions)

    private fun stub(memberType: MemberType, start: LocalDate, paid: Boolean, email: String = "a@b.nl") {
        stubUser(paid, email)
        whenever(memberships.findByUserId(USER_ID)).thenReturn(mutableListOf(membership(memberType, start)))
    }

    private fun stubUser(paid: Boolean, email: String = "a@b.nl") {
        whenever(users.findById(USER_ID)).thenReturn(user(email))
        whenever(contributions.existsByUserIdAndPeriodId(USER_ID, PERIOD_ID)).thenReturn(paid)
    }

    private fun user(email: String): User = User(
        username = "member",
        email = email,
        password = "hash",
        initials = "A",
        firstName = "Alex",
        lastName = "Member",
    ).apply { setField(this, "id", USER_ID) }

    private fun membership(memberType: MemberType, startDate: LocalDate): Membership = Membership(
        user = mock(),
        startDate = startDate,
        endDate = null,
        memberType = memberType,
        incasso = false,
    ).apply {
        setField(this, "createdAt", Instant.parse("2024-01-01T00:00:00Z"))
        setField(this, "updatedAt", Instant.parse("2024-01-01T00:00:00Z"))
    }

    private fun setField(target: Any, name: String, value: Any?) {
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            runCatching {
                cls!!.getDeclaredField(name).apply { isAccessible = true }.set(target, value)
            }.onSuccess { return }
            cls = cls.superclass
        }
        error("field $name not found on ${target.javaClass}")
    }

    private companion object {
        const val USER_ID = 7L
        const val PERIOD_ID = 100L
    }
}
