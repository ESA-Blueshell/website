package net.blueshell.api.factory.board.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class BoardFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(
        name: String = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): Board {
        return Board(
            candidate = candidate,
            startDate = startDate,
            name = name,
        )
    }

    fun create(
        name: String = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): Board {
        return persistence.persist(build(name, candidate, startDate))
    }

    /** A seat may name somebody with no account, which is most of the association's history. */
    fun buildMember(
        board: Board,
        user: User? = null,
        role: String = "CHAIR",
        startDate: LocalDate = LocalDate.now().minusDays(1),
        displayName: String? = null,
    ): BoardMember {
        return BoardMember(
            board = board,
            user = user,
            role = role,
            startDate = startDate,
            displayName = displayName,
        )
    }

    fun createMember(
        board: Board,
        user: User? = null,
        role: String = "CHAIR",
        startDate: LocalDate = LocalDate.now().minusDays(1),
        displayName: String? = null,
    ): BoardMember {
        return persistence.persist(buildMember(board, user, role, startDate, displayName))
    }
}
