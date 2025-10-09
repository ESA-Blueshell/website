package net.blueshell.api.service.survey;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.survey.Answer;
import net.blueshell.api.model.survey.Survey;
import net.blueshell.api.repository.survey.AnswerRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AnswerService extends BaseModelService<Answer, AnswerRepository> {

    @Autowired
    public AnswerService(AnswerRepository repository) {
        super(repository);
    }


    public Set<Answer> findBySurveyId(Long surveyId) {
        return repository.findBySurveyId(surveyId);
    }
}
