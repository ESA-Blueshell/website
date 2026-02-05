package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.File;
import net.blueshell.api.model.Sponsor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Sponsor model test instances.
 */
@Component
@RequiredArgsConstructor
public class SponsorFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final FileFactory fileFactory;

    public Sponsor createBasic() {
        Sponsor s = new Sponsor();
        s.setName(faker.company().name());
        s.setDescription(faker.lorem().paragraph(3));
        File logo = fileFactory.createImage();
        s.setPicture(logo);
        return s;
    }

    public Sponsor createFull() {
        return createBasic();
    }

    public Sponsor createWithCustomizations(java.util.function.Consumer<Sponsor> customizer) {
        Sponsor s = createFull();
        customizer.accept(s);
        return s;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
