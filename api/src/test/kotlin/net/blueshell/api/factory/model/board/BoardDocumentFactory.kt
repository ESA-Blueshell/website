package net.blueshell.api.factory.model.board

import com.github.javafaker.Faker
import net.blueshell.api.board.persistence.BoardDocument
import net.blueshell.api.factory.model.FileFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicLong
import java.util.function.Consumer

/**
 * Factory for BoardDocument model test instances.
 */
@Component
class BoardDocumentFactory(
    private val faker: Faker,
    private val boardFactory: BoardFactory,
    private val fileFactory: FileFactory
) {

    fun createBasic(): BoardDocument {
        val boardDocument = BoardDocument()
        boardDocument.board = boardFactory.createBasic()
        boardDocument.name = faker.book().title() + ".pdf"
        boardDocument.file = fileFactory.createDocument()
        return boardDocument
    }

    fun createFull(): BoardDocument = createBasic()

    fun createWithCustomizations(customizer: Consumer<BoardDocument>): BoardDocument {
        val boardDocument = createFull()
        customizer.accept(boardDocument)
        return boardDocument
    }

    private fun generateId(): Long = COUNTER.incrementAndGet()

    private companion object {
        val COUNTER = AtomicLong(1000)
    }
}
