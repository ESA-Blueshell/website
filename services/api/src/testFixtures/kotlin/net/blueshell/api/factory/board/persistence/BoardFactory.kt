package net.blueshell.api.factory.board.persistence

import net.blueshell.api.factory.support.FactoryPersistenceSupport
import net.blueshell.api.board.persistence.Board
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.user.persistence.User
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

@Component
class BoardFactory(
    private val persistence: FactoryPersistenceSupport
) {
    fun build(
        name: String? = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1),
        number: Int = numbers.incrementAndGet(),
    ): Board {
        return Board(
            number = number,
            candidate = candidate,
            startDate = startDate,
            name = name,
        )
    }

    fun create(
        name: String? = "Board ${System.currentTimeMillis()}",
        candidate: String = "Candidate",
        startDate: LocalDate = LocalDate.now().minusDays(1),
        number: Int = numbers.incrementAndGet(),
    ): Board {
        return persistence.persist(build(name, candidate, startDate, number))
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

    private companion object {
        /**
         * A number nobody else in the run holds. A board's number is unique among the boards
         * that exist, and the wipe between tests never brings one back, so counting up is
         * enough to keep two fixtures out of each other's way.
         */
        val numbers = AtomicInteger(100)
    }
}
