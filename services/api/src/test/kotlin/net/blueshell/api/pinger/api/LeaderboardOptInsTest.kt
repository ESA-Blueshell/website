package net.blueshell.api.pinger.api

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.blueshell.api.pinger.persistence.PingerBoardMember
import net.blueshell.api.pinger.persistence.PingerBoardMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LeaderboardOptInsTest {
    private val now = Instant.parse("2026-10-07T12:00:00Z")
    private val members = mockk<PingerBoardMemberRepository>()
    private val optIns = LeaderboardOptIns(members, Clock.fixed(now, ZoneOffset.UTC))

    @Test
    fun `a member with no row is not opted in`() {
        every { members.findByMemberId(7) } returns null

        assertThat(optIns.isOptedIn(7)).isFalse()
    }

    @Test
    fun `a member with an opted-in row is opted in`() {
        every { members.findByMemberId(7) } returns PingerBoardMember(memberId = 7, optedIn = true, updated = Instant.EPOCH)

        assertThat(optIns.isOptedIn(7)).isTrue()
    }

    @Test
    fun `opting in for the first time opens a row set to true`() {
        every { members.findByMemberId(7) } returns null
        val saved = slot<PingerBoardMember>()
        every { members.save(capture(saved)) } answers { saved.captured }

        optIns.setOptedIn(7, optedIn = true)

        assertThat(saved.captured.memberId).isEqualTo(7)
        assertThat(saved.captured.optedIn).isTrue()
        assertThat(saved.captured.updated).isEqualTo(now)
    }

    @Test
    fun `opting out flips the existing row and keeps it`() {
        val existing = PingerBoardMember(memberId = 7, optedIn = true, updated = Instant.EPOCH)
        every { members.findByMemberId(7) } returns existing
        every { members.save(existing) } returns existing

        optIns.setOptedIn(7, optedIn = false)

        assertThat(existing.optedIn).isFalse()
        assertThat(existing.updated).isEqualTo(now)
        verify { members.save(existing) }
    }
}
