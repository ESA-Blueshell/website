package net.blueshell.api.event.persistence

import net.blueshell.api.survey.persistence.Answer
import net.blueshell.api.survey.persistence.Question
import net.blueshell.api.survey.persistence.Survey
import net.blueshell.api.shared.enums.QuestionType
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest

/**
 * `event_sign_up_answers` has one mapping: the join table behind [EventSignUp.answers].
 *
 * Both ways an answer leaves a sign-up must leave the table in a state that mapping agrees
 * with — the collection and the rows behind it must never disagree about what is still linked.
 */
@SpringBootTest
class EventSignUpAnswerLinkIT : UserTestSupport() {

    @Test
    fun `removing an answer from a sign-up removes its link row`() {
        val question = persistQuestion()
        val signUp = persistSignUpWithAnswers(question, "first", "second")
        assertThat(linkRows(signUp.id!!)).isEqualTo(2)

        val remaining = transactionTemplate.execute {
            val managed = entityManager.find(EventSignUp::class.java, signUp.id)
            val answers = managed.answers as MutableSet
            answers.remove(answers.first())
            entityManager.flush()
            managed.answers.size
        }

        assertThat(remaining).isEqualTo(1)
        assertThat(linkRows(signUp.id!!))
            .describedAs("a removed answer leaves no row the collection would still load")
            .isEqualTo(1)
    }

    @Test
    fun `soft-deleting an answer hides it from the sign-up`() {
        val question = persistQuestion()
        val signUp = persistSignUpWithAnswers(question, "only")
        val answerId = visibleAnswerIds(signUp.id!!).single()

        transactionTemplate.execute {
            entityManager.remove(entityManager.find(Answer::class.java, answerId))
            entityManager.flush()
        }

        assertThat(visibleAnswerIds(signUp.id!!))
            .describedAs("the answer's own soft-delete takes it out of the collection")
            .isEmpty()
        assertThat(deletedAnswers(answerId))
            .describedAs("the answer is soft-deleted, not gone")
            .isEqualTo(1)
    }

    private fun persistQuestion(): Question {
        val survey = persist(Survey())
        return persist(Question(idx = 0, survey = survey, type = QuestionType.OPEN, label = "Why?"))
    }

    private fun persistSignUpWithAnswers(question: Question, vararg responses: String): EventSignUp {
        val signUp = createEventSignUpFixture()
        val answers = signUp.answers as MutableSet
        responses.forEach { answers.add(Answer(question = question, textResponse = it)) }
        return persist(signUp)
    }

    private fun visibleAnswerIds(signUpId: Long): List<Long> =
        transactionTemplate.execute {
            entityManager.clear()
            entityManager.find(EventSignUp::class.java, signUpId).answers.mapNotNull { it.id }
        }!!

    private fun linkRows(signUpId: Long): Long =
        countQuery("SELECT COUNT(*) FROM event_sign_up_answers WHERE event_sign_up_id = $signUpId")

    private fun deletedAnswers(answerId: Long): Long =
        countQuery("SELECT COUNT(*) FROM answers WHERE id = $answerId AND deleted_at <> '9999-12-31 23:59:59'")

    private fun countQuery(sql: String): Long =
        transactionTemplate.execute {
            (entityManager.createNativeQuery(sql).singleResult as Number).toLong()
        }!!
}
