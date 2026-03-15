package net.blueshell.api.factory.board.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.domain.board.persistence.Board
import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.user.persistence.User
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

    fun buildMember(
        board: Board,
        user: User,
        role: String = "CHAIR",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): BoardMember {
        return BoardMember(
            id = BoardMember.Id(board.id, user.id),
            board = board,
            user = user,
            role = role,
            startDate = startDate
        )
    }

    fun createMember(
        board: Board,
        user: User,
        role: String = "CHAIR",
        startDate: LocalDate = LocalDate.now().minusDays(1)
    ): BoardMember {
        return persistence.persist(buildMember(board, user, role, startDate))
    }
}
