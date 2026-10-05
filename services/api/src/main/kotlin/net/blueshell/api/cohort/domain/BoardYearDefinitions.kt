package net.blueshell.api.cohort.domain

import net.blueshell.api.board.api.BoardMemberService
import net.blueshell.api.board.api.BoardYear
import net.blueshell.api.cohort.persistence.CohortType
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Everybody who sat on one board, named by its years: "Board 2024-2025". The board in office holds
 * the Board role as well; this one stays with them after they hand over, which is what the server's
 * role for each board year is for.
 */
class BoardYearDefinition(
    private val boardMembers: BoardMemberService,
    private val board: BoardYear,
) : CohortDefinition {
    override val key = "${CohortType.BOARD_YEAR_MEMBERS}:${board.boardId}"
    override val type = CohortType.BOARD_YEAR_MEMBERS
    override val scope = board.boardId
    override val label = "Board ${board.years}"
    override val folder = CohortFolders.BOARD

    override fun members(): Set<Long> = boardMembers.everOn(board.boardId)

    override fun contains(userId: Long): Boolean = userId in members()
}

/** One definition for each board that has taken office; a candidate board gets its own on its first day. */
@Component
class BoardYearProvider(
    private val boardMembers: BoardMemberService,
) : CohortDefinitionProvider {
    override val type = CohortType.BOARD_YEAR_MEMBERS

    override fun definitions(): List<CohortDefinition> =
        boardMembers.boardYearsBy(LocalDate.now()).map { BoardYearDefinition(boardMembers, it) }
}
