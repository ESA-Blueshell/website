package net.blueshell.api.event;

import net.blueshell.api.common.event.PostPersistEvent;
import net.blueshell.api.common.event.PostRemoveEvent;
import net.blueshell.api.common.event.PostUpdateEvent;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.service.survey.AnswerService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class QuestionEventListener {

    private final AnswerService answers;

    public QuestionEventListener(AnswerService answers) {
        this.answers = answers;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PostPersistEvent<Question> evt) {
        var q = evt.getSource();

        if (q.getSurveyId() != null) {
            answers.deleteAll(answers.findBySurveyId(q.getSurveyId()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Question> evt) {
        var q = evt.getSource();

        // When a question is updated, the survey will need to be re-filled.
        // Therefore, all answers for the survey need to be wiped.
        if (q.getAnswers() != null && !q.getAnswers().isEmpty()) {
            answers.deleteAll(answers.findBySurveyId(q.getSurveyId()));
        }
    }


    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDelete(PostRemoveEvent<Question> evt) {
        var q = evt.getSource();

        // If a question is just removed, without anything else being changed
        // There is no need to wipe all existing answers for the survey.
        // All existing answers only need to be wiped if a new question is added to a survey
        // Or if a existing question is modified and thus all questions need to be re-answered
        if (q.getAnswers() != null && !q.getAnswers().isEmpty()) {
            answers.deleteAll(q.getAnswers());
        }
    }
}
