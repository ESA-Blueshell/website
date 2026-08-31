package net.blueshell.api.user.domain

import net.blueshell.api.shared.dto.bulk.BulkRowDisposition
import net.blueshell.api.shared.dto.bulk.BulkRowReason
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * The rules the two bulk membership actions run on, stated directly.
 *
 * These are the same decisions the preview renders and the apply acts on, which is why
 * they are worth pinning here rather than only inferring them from what a batch did.
 */
class BulkMembershipDecisionsTest {

    private val today: LocalDate = LocalDate.of(2026, 8, 31)

    // The rules read dates, ids and types off a membership and never the member behind it,
    // so one stand-in serves every case here.
    private val holder = User(
        username = "returner",
        email = "returner@example.com",
        password = "irrelevant",
        initials = "R.",
        firstName = "Ria",
        lastName = "Returner",
    )

    private fun membership(
        id: Long = 1,
        startDate: LocalDate = today.minusYears(1),
        endDate: LocalDate? = null,
        memberType: MemberType = MemberType.REGULAR,
        incasso: Boolean = true,
    ) = Membership(
        user = holder,
        startDate = startDate,
        endDate = endDate,
        memberType = memberType,
        incasso = incasso,
    ).also { it.id = id }

    private fun decideEnd(vararg held: Membership) =
        BulkMembershipDecisions.decide(BulkMembershipOperation.END, held.toList(), today)

    private fun decideStart(vararg held: Membership) =
        BulkMembershipDecisions.decide(BulkMembershipOperation.START, held.toList(), today)

    @Test
    fun `ending names the active membership it will close`() {
        val decision = decideEnd(membership(id = 7))

        assertThat(decision).isEqualTo(BulkMembershipDecision.End(listOf(7L)))
        assertThat(decision.disposition).isEqualTo(BulkRowDisposition.INCLUDED)
    }

    @Test
    fun `ending skips a member who holds no membership at all`() {
        assertThat(decideEnd()).isEqualTo(BulkMembershipDecision.Skip(BulkRowReason.NO_ACTIVE_MEMBERSHIP))
    }

    @Test
    fun `ending skips a member whose every membership is already closed`() {
        val decision = decideEnd(membership(endDate = today.minusDays(1)))

        assertThat(decision).isEqualTo(BulkMembershipDecision.Skip(BulkRowReason.NO_ACTIVE_MEMBERSHIP))
    }

    @Test
    fun `ending skips a membership that only started today, which has no day to span`() {
        val decision = decideEnd(membership(startDate = today))

        assertThat(decision).isEqualTo(BulkMembershipDecision.Skip(BulkRowReason.STARTED_TODAY))
    }

    @Test
    fun `ending leaves closed spells out of the set it will write to`() {
        val decision = decideEnd(
            membership(id = 1, startDate = today.minusYears(5), endDate = today.minusYears(4)),
            membership(id = 2, startDate = today.minusYears(3)),
        )

        assertThat(decision).isEqualTo(BulkMembershipDecision.End(listOf(2L)))
    }

    @Test
    fun `starting opens a regular membership for somebody who has never been a member`() {
        val decision = decideStart()

        assertThat(decision).isEqualTo(BulkMembershipDecision.Start(MemberType.REGULAR))
        assertThat(decision.reason).isEqualTo(BulkRowReason.WILL_START_NEW)
    }

    @Test
    fun `starting skips a member who is already active`() {
        assertThat(decideStart(membership())).isEqualTo(BulkMembershipDecision.Skip(BulkRowReason.ALREADY_ACTIVE))
    }

    @Test
    fun `a returning member comes back on the type of their most recent spell`() {
        val decision = decideStart(
            membership(id = 1, startDate = today.minusYears(5), endDate = today.minusYears(4)),
            membership(
                id = 2,
                startDate = today.minusYears(3),
                endDate = today.minusYears(2),
                memberType = MemberType.ALUMNI,
            ),
        )

        assertThat(decision).isEqualTo(BulkMembershipDecision.Start(MemberType.ALUMNI))
    }

    /**
     * An incasso mandate is a standing authorisation to take money. One given for a
     * membership that then ended is not re-armed on somebody's behalf in a batch.
     */
    @Test
    fun `a returning member does not have their old incasso mandate re-armed`() {
        val decision = decideStart(membership(endDate = today.minusYears(2), incasso = true))

        assertThat(decision).isInstanceOf(BulkMembershipDecision.Start::class.java)
        // Start carries only the type; the mandate is not in it, so nothing can carry it over.
        assertThat((decision as BulkMembershipDecision.Start).memberType).isEqualTo(MemberType.REGULAR)
    }
}
