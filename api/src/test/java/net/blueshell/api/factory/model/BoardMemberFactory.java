package net.blueshell.api.factory.model;

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

    public BoardMember createBasic() {
        BoardMember bm = new BoardMember();
        bm.setId(generateId());

        Board board = boardFactory.createBasic();
        User user = userFactory.createFull();
        File picture = fileFactory.createImage();

        bm.setBoard(board);
        bm.setBoardId(board.getId());
        bm.setUser(user);
        bm.setUserId(user.getId());
        bm.setPicture(picture);

        return bm;
    }

    public BoardMember createFull() {
        return createBasic();
    }

    public BoardMember createWithCustomizations(java.util.function.Consumer<BoardMember> customizer) {
        BoardMember bm = createFull();
        customizer.accept(bm);
        return bm;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
