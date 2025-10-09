package net.blueshell.api.service.survey;

import net.blueshell.api.base.BaseModelService;
import net.blueshell.api.model.survey.Answer;
import net.blueshell.api.model.survey.Question;
import net.blueshell.api.repository.survey.QuestionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QuestionService extends BaseModelService<Question, QuestionRepository> {

    @Autowired
    public QuestionService(QuestionRepository repository) {
        super(repository);
    }
}
