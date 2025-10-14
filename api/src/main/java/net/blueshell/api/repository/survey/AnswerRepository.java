package net.blueshell.api.repository.survey;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.survey.Answer;
import org.springframework.stereotype.Repository;

import java.util.Set;

@Repository
public interface AnswerRepository extends BaseRepository<Answer> {
    Set<Answer> findByQuestionSurveyId(Long surveyId);
}