package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.model.survey.Survey;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Question model test instances.
 */
@Component
@RequiredArgsConstructor
public class QuestionFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;

    public Question createBasic() {
        Question question = new Question();
        ModelTestUtils.setId(question, generateId());
        question.setIdx((long) faker.number().numberBetween(1, 100));
        question.setType(faker.options().option(QuestionType.class));
        question.setLabel(faker.lorem().sentence() + "?");
        question.setAnswerCount(0L);
        return question;
    }

    public Question createFull() {
        Question question = createBasic();
        if (question.getType() == QuestionType.CHECKBOX || question.getType() == QuestionType.RADIO) {
            question.setChoiceLabels(generateChoiceLabels());
        }
        return question;
    }

    public Question createWithCustomizations(java.util.function.Consumer<Question> customizer) {
        Question question = createFull();
        customizer.accept(question);
        return question;
    }

    public Question createForSurvey(Survey survey) {
        return createWithCustomizations(question -> {
            question.setSurvey(survey);
            question.setSurveyId(survey.getId());
        });
    }

    public Question createMultipleChoice() {
        return createWithCustomizations(question -> {
            question.setType(QuestionType.RADIO);
            question.setChoiceLabels(Arrays.asList("Option A", "Option B", "Option C", "Option D"));
        });
    }

    public Question createText() {
        return createWithCustomizations(question -> {
            question.setType(QuestionType.OPEN);
            question.setChoiceLabels(null);
        });
    }

    private List<String> generateChoiceLabels() {
        int count = faker.number().numberBetween(2, 6);
        return Arrays.asList(
                "Strongly Disagree", "Disagree", "Neutral", "Agree", "Strongly Agree"
        ).subList(0, count);
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
