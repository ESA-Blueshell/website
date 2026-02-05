package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.model.survey.Survey;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Survey model test instances.
 */
@Component
@RequiredArgsConstructor
public class SurveyFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final QuestionFactory questionFactory;

    public Survey createBasic() {
        Survey survey = new Survey();
        survey.setResponseCount(0L);
        return survey;
    }

    public Survey createFull() {
        Survey survey = createBasic();
        for (int i = 0; i < faker.number().numberBetween(3, 8); i++) {
            survey.getQuestions().add(questionFactory.createForSurvey(survey));
        }
        return survey;
    }

    public Survey createWithCustomizations(java.util.function.Consumer<Survey> customizer) {
        Survey survey = createFull();
        customizer.accept(survey);
        return survey;
    }

    public Survey createWithQuestions(int count) {
        return createWithCustomizations(survey -> {
            survey.getQuestions().clear();
            for (int i = 0; i < count; i++) {
                survey.getQuestions().add(questionFactory.createForSurvey(survey));
            }
        });
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
