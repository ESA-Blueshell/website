package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.event.EventSignUp;
import net.blueshell.api.model.event.EventSignUpAnswer;
import net.blueshell.api.model.survey.Answer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for EventSignUpAnswer model test instances.
 */
@Component
@RequiredArgsConstructor
public class EventSignUpAnswerFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final EventSignUpFactory eventSignUpFactory;
    private final AnswerFactory answerFactory;

    public EventSignUpAnswer createBasic() {
        EventSignUpAnswer esa = new EventSignUpAnswer();
        EventSignUp es = eventSignUpFactory.createBasic();
        Answer answer = answerFactory.createBasic();
        esa.setEventSignUp(es);
        esa.setAnswer(answer);
        return esa;
    }

    public EventSignUpAnswer createFull() {
        return createBasic();
    }

    public EventSignUpAnswer createWithCustomizations(java.util.function.Consumer<EventSignUpAnswer> customizer) {
        EventSignUpAnswer esa = createFull();
        customizer.accept(esa);
        return esa;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
