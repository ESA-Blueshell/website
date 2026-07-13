package net.blueshell.api.domain.user.application.validation

import net.blueshell.api.domain.user.application.exception.InvalidMembershipException
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate

class MembershipInvariantsTest {

    private val repository = mock<MemberRepository>()
    private val invariants = MembershipInvariants(repository)

    private fun existing(id: Long, start: LocalDate, end: LocalDate?): Membership =
        Membership(user = mock<User>(), startDate = start, endDate = end).apply { this.id = id }

    private fun givenOthers(vararg memberships: Membership) {
        whenever(repository.findByUser_Id(1L)).thenReturn(memberships.toMutableList())
    }

    @Test
    fun `accepts a closed interval that does not overlap existing memberships`() {
        givenOthers(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))

        assertThatCode {
            invariants.validate(1L, null, LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects startDate equal to endDate`() {
        givenOthers()

        assertThatThrownBy {
            invariants.validate(1L, null, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 1))
        }.isInstanceOf(InvalidMembershipException::class.java)
            .hasMessageContaining("before end date")
    }

    @Test
    fun `rejects endDate before startDate`() {
        givenOthers()

        assertThatThrownBy {
            invariants.validate(1L, null, LocalDate.of(2022, 6, 1), LocalDate.of(2022, 1, 1))
        }.isInstanceOf(InvalidMembershipException::class.java)
    }

    @Test
    fun `rejects a second active membership`() {
        givenOthers(existing(2L, LocalDate.of(2020, 1, 1), null))

        assertThatThrownBy {
            invariants.validate(1L, null, LocalDate.of(2022, 1, 1), null)
        }.isInstanceOf(InvalidMembershipException::class.java)
            .hasMessageContaining("already has an active membership")
    }

    @Test
    fun `allows touching endpoints because end dates are exclusive`() {
        // existing ends 2021-01-01; new starts 2021-01-01 -> no overlap under half-open semantics
        givenOthers(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))

        assertThatCode {
            invariants.validate(1L, null, LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1))
        }.doesNotThrowAnyException()
    }

    @Test
    fun `rejects overlapping intervals`() {
        givenOthers(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))

        assertThatThrownBy {
            invariants.validate(1L, null, LocalDate.of(2020, 6, 1), LocalDate.of(2020, 9, 1))
        }.isInstanceOf(InvalidMembershipException::class.java)
            .hasMessageContaining("overlaps")
    }

    @Test
    fun `rejects a new open interval overlapping an existing closed interval`() {
        givenOthers(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))

        assertThatThrownBy {
            invariants.validate(1L, null, LocalDate.of(2020, 6, 1), null)
        }.isInstanceOf(InvalidMembershipException::class.java)
    }

    @Test
    fun `excludes the membership being edited from its own checks`() {
        val self = existing(5L, LocalDate.of(2020, 1, 1), null)
        givenOthers(self)

        // Reopening/keeping membership 5 active must not conflict with itself
        assertThatCode {
            invariants.validate(1L, 5L, LocalDate.of(2020, 1, 1), null)
        }.doesNotThrowAnyException()
    }
}
