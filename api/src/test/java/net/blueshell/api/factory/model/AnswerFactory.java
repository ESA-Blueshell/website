package net.blueshell.api.factory.model;

import net.blueshell.api.testutil.ModelTestUtils;
import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.model.survey.Answer;
import net.blueshell.api.model.survey.Question;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for Answer model test instances.
 */
@Component
@RequiredArgsConstructor
public class AnswerFactory {

    private static final AtomicLong counter = new AtomicLong(1000);
    private final Faker faker;
    private final QuestionFactory questionFactory;

    public Answer createBasic() {
        Answer answer = new Answer();
        ModelTestUtils.setId(answer, generateId());

        Question q = questionFactory.createFull();
        answer.setQuestionId(q.getId());

        if (q.getType() == QuestionType.CHECKBOX || q.getType() == QuestionType.RADIO) {
            List<Boolean> selections = new ArrayList<>();
            if (q.getChoiceLabels() != null) {
                for (int i = 0; i < q.getChoiceLabels().size(); i++) {
                    selections.add(faker.bool().bool());
                }
            }
            answer.setOptionSelections(selections);
            answer.setTextResponse(null);
        } else {
            answer.setOptionSelections(null);
            answer.setTextResponse(faker.lorem().sentence());
        }
        return answer;
    }

    public Answer createFull() {
        return createBasic();
    }

    public Answer createWithCustomizations(java.util.function.Consumer<Answer> customizer) {
        Answer a = createFull();
        customizer.accept(a);
        return a;
    }

    private Long generateId() {
        return counter.incrementAndGet();
    }
}
