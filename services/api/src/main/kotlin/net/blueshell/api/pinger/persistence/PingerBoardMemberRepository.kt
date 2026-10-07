package net.blueshell.api.pinger.persistence

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PingerBoardMemberRepository : JpaRepository<PingerBoardMember, Long> {
    fun findByMemberId(memberId: Long): PingerBoardMember?

    @Query("select b.memberId from PingerBoardMember b where b.optedIn = true")
    fun findOptedInMemberIds(): List<Long>
}
