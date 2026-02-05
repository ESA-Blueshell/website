package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import net.blueshell.api.model.board.Board;
import net.blueshell.api.model.board.BoardMember;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for BoardMember model test instances.
 */
@Component
@RequiredArgsConstructor
public class BoardMemberFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final BoardFactory boardFactory;
    private final UserFactory userFactory;
    private final FileFactory fileFactory;

    public BoardMember createBasic(
            Board board,
            User user
    ) {
        BoardMember bm = new BoardMember();

        File picture = fileFactory.createImage();

        bm.setBoard(board);
        bm.setUser(user);
        bm.setPicture(picture);

        return bm;
    }

    public BoardMember createFull(Board board, User user) {
        return createBasic(board, user);
    }

    public BoardMember createWithCustomizations(Board board, User user, java.util.function.Consumer<BoardMember> customizer) {
        BoardMember bm = createFull(board, user);
        customizer.accept(bm);
        return bm;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
