package net.blueshell.api.pinger.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import net.blueshell.api.shared.model.AutoIdEntity
import java.time.Instant

/**
 * A member's choice to appear on the public contribution leaderboard. The board is opt-in: a member
 * with no row here, or a row set to false, is not shown. Only the member themselves sets [optedIn].
 */
@Entity
@Table(name = "pinger_board_member")
class PingerBoardMember(
    @Column(name = "member_id", nullable = false, unique = true)
    val memberId: Long,
    @Column(name = "opted_in", nullable = false)
    var optedIn: Boolean,
    @Column(name = "updated", nullable = false)
    var updated: Instant,
) : AutoIdEntity()
