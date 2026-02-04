package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.board.BoardDocument;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for BoardDocument model test instances.
 */
@Component
@RequiredArgsConstructor
public class BoardDocumentFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final BoardFactory boardFactory;
    private final FileFactory fileFactory;

    public BoardDocument createBasic() {
        BoardDocument bd = new BoardDocument();
        ModelTestUtils.setId(bd, generateId());
        bd.setBoard(boardFactory.createBasic());
        bd.setName(faker.book().title() + ".pdf");
        bd.setFile(fileFactory.createDocument());
        return bd;
    }

    public BoardDocument createFull() {
        return createBasic();
    }

    public BoardDocument createWithCustomizations(java.util.function.Consumer<BoardDocument> customizer) {
        BoardDocument bd = createFull();
        customizer.accept(bd);
        return bd;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
