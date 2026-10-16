package net.blueshell.api.user.domain

import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipChanged
import net.blueshell.api.user.api.MembershipService
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.time.LocalDate

class MandateEndsWithMembershipTest {
    private val memberships: MembershipService = mock()
    private val mandates: Mandates = mock()
    private val listener = MandateEndsWithMembership(memberships, mandates)

    private fun changed(change: MembershipChange) = MembershipChanged(7, false, change)

    @Test
    fun `the mandate ends on the day the last membership ends`() {
        whenever(memberships.findByUserId(7)).thenReturn(
            mutableListOf(
                Entities.membership(startDate = LocalDate.of(2020, 9, 1), endDate = LocalDate.of(2021, 9, 1)),
                Entities.membership(startDate = LocalDate.of(2022, 9, 1), endDate = LocalDate.of(2026, 9, 1)),
            ),
        )

        listener.onChange(changed(MembershipChange.UPDATED))

        verify(mandates).end(7, LocalDate.of(2026, 9, 1))
    }

    @Test
    fun `a running membership keeps it, and removing the last one ends it today, as the mandates' own day`() {
        whenever(memberships.findByUserId(7)).thenReturn(mutableListOf(Entities.membership()))
        listener.onChange(changed(MembershipChange.UPDATED))
        verifyNoInteractions(mandates)

        whenever(memberships.findByUserId(7)).thenReturn(mutableListOf())
        listener.onChange(changed(MembershipChange.DELETED))
        verify(mandates).end(7, null)
    }

    @Test
    fun `starting a membership leaves a signup's mandate alone`() {
        listener.onChange(changed(MembershipChange.CREATED))

        verifyNoInteractions(mandates, memberships)
    }
}
