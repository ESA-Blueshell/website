package net.blueshell.api.repository.survey;

import net.blueshell.api.base.BaseRepository;
import net.blueshell.api.model.survey.Question;
import org.springframework.stereotype.Repository;

@Repository
public interface QuestionRepository extends BaseRepository<Question> {
}