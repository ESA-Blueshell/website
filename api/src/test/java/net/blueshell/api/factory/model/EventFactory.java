package net.blueshell.api.factory.model;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.committee.Committee;
import net.blueshell.api.model.event.Event;
import net.blueshell.api.model.survey.Survey;
import net.blueshell.api.testutil.ModelTestUtils;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Event model test instances.
 */
@Component
@RequiredArgsConstructor
public class EventFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final CommitteeFactory committeeFactory;
    private final SurveyFactory surveyFactory;

    public Event createBasic() {
        Event event = new Event();
        event.setTitle(faker.book().title() + " Event");
        event.setDescription(faker.lorem().paragraph(5));
        event.setLocation(faker.address().fullAddress());
        event.setStartTime(Instant.now().plus(7, ChronoUnit.DAYS));
        event.setEndTime(Instant.now().plus(8, ChronoUnit.DAYS));
        event.setApproved(faker.bool().bool());
        event.setMembersOnly(faker.bool().bool());
        event.setSignUp(faker.bool().bool());

        Committee committee = committeeFactory.createBasic();
        event.setCommittee(committee);
        event.setCommitteeId(committee.getId());

        return event;
    }

    public Event createFull() {
        Event event = createBasic();
        event.setMemberPrice(faker.number().randomDouble(2, 0, 50));
        event.setPublicPrice(faker.number().randomDouble(2, 0, 100));
        event.setGoogleId(faker.internet().uuid());

        if (event.getSignUp()) {
            Survey survey = surveyFactory.createBasic();
            event.setSignUpForm(survey);
            event.setSignUpFormId(survey.getId());
        }
        return event;
    }

    public Event createWithCustomizations(java.util.function.Consumer<Event> customizer) {
        Event event = createFull();
        customizer.accept(event);
        return event;
    }

    public Event createApproved() {
        return createWithCustomizations(event -> event.setApproved(true));
    }

    public Event createWithSignUp() {
        return createWithCustomizations(event -> {
            event.setSignUp(true);
            Survey survey = surveyFactory.createBasic();
            event.setSignUpForm(survey);
            event.setSignUpFormId(survey.getId());
        });
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
