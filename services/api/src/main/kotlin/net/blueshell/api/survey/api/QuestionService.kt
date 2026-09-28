package net.blueshell.api.survey.api

import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.QuestionRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** The questions of a sign-up form, as the modules that answer one read them. */
@Service
class QuestionService(
    private val questions: QuestionRepository,
) {
    /** The question, or nothing where there is none. */
    @Transactional(readOnly = true)
    fun find(id: Long): Question? = questions.findById(id).orElse(null)

    /**
     * A lazy reference to a question, for an answer that only needs its id. Nothing is read
     * until the question itself is.
     */
    @Transactional(readOnly = true)
    fun getReferenceById(id: Long): Question = questions.getReferenceById(id)
}
