package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.User;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.survey.Answer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for EventSignUp model test instances.
 */
@Component
@RequiredArgsConstructor
public class EventSignUpFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final EventFactory eventFactory;
    private final UserFactory userFactory;
    private final AnswerFactory answerFactory;

    public EventSignUp createBasic() {
        EventSignUp es = new EventSignUp();

        Event event = eventFactory.createFull();
        User user = userFactory.createFull();

        es.setEvent(event);
        es.setUser(user);
        es.setUserId(user.getId());
        es.setGuest(null);

        es.getAnswers().add(answerFactory.createBasic());

        return es;
    }

    public EventSignUp createFull() {
        return createBasic();
    }

    public EventSignUp createWithCustomizations(java.util.function.Consumer<EventSignUp> customizer) {
        EventSignUp es = createFull();
        customizer.accept(es);
        return es;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
