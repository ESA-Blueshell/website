package net.blueshell.api.contribution.domain

import net.blueshell.api.contribution.api.ContributionPeriodService
import net.blueshell.api.contribution.api.ContributionService
import net.blueshell.api.contribution.persistence.Contribution
import net.blueshell.api.contribution.persistence.ContributionPeriod
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.api.UserService
import net.blueshell.api.user.api.UserErasureService
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import net.blueshell.api.shared.dto.bulk.BulkSelectionRejected
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class BulkContributionUseCasesTest {

    private val contributions = mock<ContributionService>()
    private val users = mock<UserService>()
    private val memberships = mock<MembershipService>()
    private val periods = mock<ContributionPeriodService>()
    private val erasure = mock<UserErasureService>()
    private val useCases =
        BulkContributionUseCases(contributions, users, memberships, periods, erasure)

    private val periodId = 100L

    @Test
    fun `records a contribution for a user who has none`() {
        knownPeriod()
        knownUser(1L)
        whenever(contributions.existsByUserIdAndPeriodId(1L, periodId)).thenReturn(false)

        val result = useCases.execute(listOf(1L), periodId, BulkContributionOperation.PAID)

        assertThat(result.applied).isEqualTo(1)
        assertThat(result.skipped).isZero()
        verify(contributions).create(any())
    }

    @Test
    fun `reports a user already in the requested state as unchanged rather than applied`() {
        knownPeriod()
        knownUser(1L)
        whenever(contributions.existsByUserIdAndPeriodId(1L, periodId)).thenReturn(true)

        val result = useCases.execute(listOf(1L), periodId, BulkContributionOperation.PAID)

        assertThat(result.applied).isZero()
        assertThat(result.skipped).isEqualTo(1)
        verify(contributions, never()).create(any())
    }

    @Test
    fun `removes a recorded contribution when marking unpaid`() {
        knownPeriod()
        knownUser(1L)
        whenever(contributions.existsByUserIdAndPeriodId(1L, periodId)).thenReturn(true)

        val result = useCases.execute(listOf(1L), periodId, BulkContributionOperation.UNPAID)

        assertThat(result.applied).isEqualTo(1)
        verify(contributions).deleteById(Contribution.Id(1L, periodId))
    }

    @Test
    fun `refuses the whole selection when a user no longer exists, naming the ids`() {
        knownPeriod()
        knownUser(1L)
        whenever(users.existsById(99L)).thenReturn(false)

        assertThatThrownBy { useCases.execute(listOf(1L, 99L), periodId, BulkContributionOperation.PAID) }
            .isInstanceOfSatisfying(BulkSelectionRejected::class.java) { rejected ->
                val violation = rejected.violations.single()
                assertThat(violation.code).isEqualTo(BulkSelectionRejected.UNKNOWN_USERS)
                assertThat(violation.field).isEqualTo("userIds")
                assertThat(violation.values).containsExactly(99L)
            }

        // Nothing is written for the users that did resolve.
        verify(contributions, never()).create(any())
    }

    @Test
    fun `refuses a selection naming a deleted user, naming the ids`() {
        // Deletion anonymises the account and keeps the row, so the id still resolves.
        knownPeriod()
        knownUser(1L)
        knownUser(7L)
        whenever(erasure.isDeleted(7L)).thenReturn(true)

        assertThatThrownBy { useCases.execute(listOf(1L, 7L), periodId, BulkContributionOperation.PAID) }
            .isInstanceOfSatisfying(BulkSelectionRejected::class.java) { rejected ->
                val violation = rejected.violations.single()
                assertThat(violation.code).isEqualTo(BulkSelectionRejected.DELETED_USERS)
                assertThat(violation.values).containsExactly(7L)
            }

        verify(contributions, never()).create(any())
    }

    @Test
    fun `refuses a selection naming an honorary member, naming the ids`() {
        knownPeriod()
        knownUser(1L)
        knownUser(2L, MemberType.HONORARY)

        assertThatThrownBy { useCases.execute(listOf(1L, 2L), periodId, BulkContributionOperation.PAID) }
            .isInstanceOfSatisfying(BulkSelectionRejected::class.java) { rejected ->
                val violation = rejected.violations.single()
                assertThat(violation.code).isEqualTo(BulkSelectionRejected.HONORARY_USERS)
                assertThat(violation.values).containsExactly(2L)
            }

        verify(contributions, never()).create(any())
    }

    @Test
    fun `reports every reason a selection was refused, not only the first`() {
        knownPeriod()
        knownUser(2L, MemberType.HONORARY)
        whenever(users.existsById(99L)).thenReturn(false)

        assertThatThrownBy { useCases.execute(listOf(2L, 99L), periodId, BulkContributionOperation.PAID) }
            .isInstanceOfSatisfying(BulkSelectionRejected::class.java) { rejected ->
                assertThat(rejected.violations.map { it.code })
                    .containsExactlyInAnyOrder(
                        BulkSelectionRejected.UNKNOWN_USERS,
                        BulkSelectionRejected.HONORARY_USERS,
                    )
            }
    }

    @Test
    fun `refuses a selection naming a period that no longer exists`() {
        whenever(periods.existsById(periodId)).thenReturn(false)

        assertThatThrownBy { useCases.execute(listOf(1L), periodId, BulkContributionOperation.PAID) }
            .isInstanceOfSatisfying(BulkSelectionRejected::class.java) { rejected ->
                val violation = rejected.violations.single()
                assertThat(violation.field).isEqualTo("contributionPeriodId")
                assertThat(violation.code).isEqualTo(BulkSelectionRejected.UNKNOWN_PERIOD)
            }

        verify(users, never()).existsById(any())
    }

    @Test
    fun `a repeated id is acted on once`() {
        knownPeriod()
        knownUser(1L)
        whenever(contributions.existsByUserIdAndPeriodId(1L, periodId)).thenReturn(false)

        val result = useCases.execute(listOf(1L, 1L, 1L), periodId, BulkContributionOperation.PAID)

        assertThat(result.applied).isEqualTo(1)
        verify(contributions).create(any())
    }


    private fun knownPeriod() {
        whenever(periods.existsById(periodId)).thenReturn(true)
        whenever(periods.findById(periodId)).thenReturn(period())
    }

    private fun knownUser(id: Long, memberType: MemberType = MemberType.REGULAR) {
        whenever(users.existsById(id)).thenReturn(true)
        whenever(users.findById(id)).thenReturn(user(id))
        whenever(memberships.findByUserId(id)).thenReturn(mutableListOf(membership(memberType)))
    }

    private fun period() = ContributionPeriod(
        startDate = LocalDate.of(2024, 1, 1),
        endDate = LocalDate.of(2024, 12, 31),
        halfYearCutoffDate = LocalDate.of(2024, 7, 1),
    ).apply { setField(this, "id", periodId) }

    private fun user(id: Long) = User(
        username = "user$id",
        email = "user$id@example.com",
        password = "hash",
        initials = "U",
        firstName = "User$id",
        lastName = "",
    ).apply { setField(this, "id", id) }

    private fun membership(memberType: MemberType) = Membership(
        user = mock(),
        startDate = LocalDate.of(2023, 1, 1),
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
            runCatching { cls!!.getDeclaredField(name).apply { isAccessible = true }.set(target, value) }
                .onSuccess { return }
            cls = cls.superclass
        }
        error("field $name not found on ${target.javaClass}")
    }
}
