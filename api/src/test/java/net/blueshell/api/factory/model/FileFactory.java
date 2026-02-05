package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.model.File;
import net.blueshell.api.model.User;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for File model test instances.
 */
@Component
@RequiredArgsConstructor
public class FileFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final UserFactory userFactory;

    public File createBasic() {
        File file = new File();
        file.setName(faker.file().fileName());
        file.setPath("/uploads/" + faker.file().fileName());
        file.setMediaType(faker.options().option("image/jpeg", "image/png", "application/pdf"));
        file.setSize(faker.number().numberBetween(1024L, 10485760L));
        file.setType(faker.options().option(FileType.class));

        User uploader = userFactory.createBasic();

        return file;
    }

    public File createFull() {
        return createBasic();
    }

    public File createWithCustomizations(java.util.function.Consumer<File> customizer) {
        File file = createFull();
        customizer.accept(file);
        return file;
    }

    public File createImage() {
        return createWithCustomizations(file -> {
            file.setMediaType(faker.options().option("image/jpeg", "image/png"));
            file.setType(FileType.EVENT_BANNER);
        });
    }

    public File createDocument() {
        return createWithCustomizations(file -> {
            file.setMediaType("application/pdf");
            file.setType(FileType.DOCUMENT);
        });
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
