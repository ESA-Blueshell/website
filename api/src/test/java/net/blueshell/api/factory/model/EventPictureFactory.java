package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.event.EventPicture;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for EventPicture model test instances.
 */
@Component
@RequiredArgsConstructor
public class EventPictureFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final EventFactory eventFactory;
    private final FileFactory fileFactory;

    public EventPicture createBasic() {
        EventPicture ep = new EventPicture();
        ep.setId(generateId());
        ep.setEvent(eventFactory.createBasic());
        ep.setPicture(fileFactory.createImage());
        return ep;
    }

    public EventPicture createFull() {
        return createBasic();
    }

    public EventPicture createWithCustomizations(java.util.function.Consumer<EventPicture> customizer) {
        EventPicture ep = createFull();
        customizer.accept(ep);
        return ep;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
