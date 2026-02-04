package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.event.EventFeedback;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for EventFeedback model test instances.
 */
@Component
@RequiredArgsConstructor
public class EventFeedbackFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final EventFactory eventFactory;

    public EventFeedback createBasic() {
        EventFeedback ef = new EventFeedback();
        ModelTestUtils.setId(ef, generateId());
        ef.setEvent(eventFactory.createBasic());
        ef.setFeedback(faker.lorem().sentence(10));
        return ef;
    }

    public EventFeedback createFull() {
        return createBasic();
    }

    public EventFeedback createWithCustomizations(java.util.function.Consumer<EventFeedback> customizer) {
        EventFeedback ef = createFull();
        customizer.accept(ef);
        return ef;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
