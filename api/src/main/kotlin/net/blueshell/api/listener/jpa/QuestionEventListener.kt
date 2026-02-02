package net.blueshell.api.listener.jpa;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.blueshell.api.common.enums.QuestionType;
import net.blueshell.api.common.event.jpa.PostPersistEvent;
import net.blueshell.api.common.event.jpa.PostRemoveEvent;
import net.blueshell.api.common.event.jpa.PostUpdateEvent;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.service.event.EventSignUpService;
import net.blueshell.api.service.survey.AnswerService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionEventListener {

    private final AnswerService answers;
    private final EventSignUpService signUps;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPersist(PostPersistEvent<Question> evt) {
        var q = evt.getSource();

        log.info("On persist question {}", q.getId());

        // If a new description is added, there is no need to clear the survey.
        // If a new question is added, then we do need to wipe the answers and signups.
        // This is because the surveys will need to be filled in again.
        if (q.getType() != QuestionType.DESCRIPTION && q.getSurveyId() != null) {
            signUps.deleteAll(signUps.findBySurveyId(q.getSurveyId()));
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onUpdate(PostUpdateEvent<Question> evt) {
        var q = evt.getSource();

        // When a question is updated, the survey will need to be re-filled.
        // Therefore, all answers for the survey need to be wiped.
        log.info("Question update dirty fields: {}", q.getDirtyFields());
        if (q.getAnswers() != null && !q.getAnswers().isEmpty() && q.isDirty()) {
            signUps.deleteAll(signUps.findBySurveyId(q.getSurveyId()));
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
