package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.File;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.event.EventBanner;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for EventBanner model test instances.
 */
@Component
@RequiredArgsConstructor
public class EventBannerFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final EventFactory eventFactory;
    private final FileFactory fileFactory;

    public EventBanner createBasic() {
        EventBanner eb = new EventBanner();

        Event event = eventFactory.createBasic();
        File file = fileFactory.createImage();

        eb.setEvent(event);
        eb.setFile(file);

        return eb;
    }

    public EventBanner createFull() {
        return createBasic();
    }

    public EventBanner createWithCustomizations(java.util.function.Consumer<EventBanner> customizer) {
        EventBanner eb = createFull();
        customizer.accept(eb);
        return eb;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
