package net.blueshell.api.survey.api

import net.blueshell.api.survey.persistence.QuestionRepository
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class QuestionServiceTest {
    private val repository = mock<QuestionRepository>()
    private val service = QuestionService(repository)

    @Test
    fun `finds a question, or nothing where there is none`() {
        val question = Entities.question()
        whenever(repository.findById(1L)).thenReturn(Optional.of(question))
        whenever(repository.findById(2L)).thenReturn(Optional.empty())

        assertThat(service.find(1L)).isSameAs(question)
        assertThat(service.find(2L)).isNull()
    }

    @Test
    fun `hands out a reference without reading the question`() {
        val reference = Entities.question()
        whenever(repository.getReferenceById(3L)).thenReturn(reference)

        assertThat(service.getReferenceById(3L)).isSameAs(reference)
    }
}
