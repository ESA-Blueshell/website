package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.File;
import net.blueshell.api.model.board.Board;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Board model test instances.
 */
@Component
@RequiredArgsConstructor
public class BoardFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final FileFactory fileFactory;

    public Board createBasic() {
        Board board = new Board();
        board.setId(generateId());
        board.setName(faker.company().name() + " Board");
        board.setCandidate(faker.name().fullName());
        board.setStartDate(LocalDate.now().minusYears(1));
        board.setEndDate(LocalDate.now().plusYears(1));
        board.setMembers(new HashSet<>());
        board.setDocuments(new HashSet<>());
        return board;
    }

    public Board createFull() {
        Board board = createBasic();
        File picture = fileFactory.createImage();
        board.setPicture(picture);
        return board;
    }

    public Board createWithCustomizations(java.util.function.Consumer<Board> customizer) {
        Board board = createFull();
        customizer.accept(board);
        return board;
    }

    public Board createCurrent() {
        return createWithCustomizations(board -> {
            board.setStartDate(LocalDate.now().minusMonths(6));
            board.setEndDate(LocalDate.now().plusMonths(6));
        });
    }

    public Board createPast() {
        return createWithCustomizations(board -> {
            board.setStartDate(LocalDate.now().minusYears(2));
            board.setEndDate(LocalDate.now().minusYears(1));
        });
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
