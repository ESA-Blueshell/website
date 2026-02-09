package net.blueshell.api.factory.model.board

import com.github.javafaker.Faker
import net.blueshell.api.factory.model.FileFactory
import net.blueshell.api.feature.board.model.Board
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for Board model test instances.
 */
@Component
class BoardFactory(
    private val faker: Faker,
    private val fileFactory: FileFactory
) {

    fun createBasic(): Board {
        val board = Board()
        board.name = faker.company().name() + " Board"
        board.candidate = faker.name().fullName()
        board.startDate = LocalDate.now().minusYears(1)
        board.endDate = LocalDate.now().plusYears(1)
        return board
    }

    fun createFull(): Board {
        val board = createBasic()
        val picture = fileFactory.createImage()
        board.picture = picture
        return board
    }

    fun createWithCustomizations(customizer: Consumer<Board>): Board {
        val board = createFull()
        customizer.accept(board)
        return board
    }

    fun createCurrent(): Board {
        return createWithCustomizations { board ->
            board.startDate = LocalDate.now().minusMonths(6)
            board.endDate = LocalDate.now().plusMonths(6)
        }
    }

    fun createPast(): Board {
        return createWithCustomizations { board ->
            board.startDate = LocalDate.now().minusYears(2)
            board.endDate = LocalDate.now().minusYears(1)
        }
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
