package net.blueshell.api.board.api

import jakarta.persistence.EntityManager
import net.blueshell.api.board.domain.BoardMemberNotFoundException
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class BoardMemberServiceTest {
    private val manager = mock<EntityManager>()
    private val repository =
        mock<BoardMemberRepository> { on { saveAndFlush(any<BoardMember>()) } doAnswer { it.getArgument(0) } }
    private val service =
        BoardMemberService(repository).also {
            BoardMemberService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(it, manager)
        }

    @Test
    fun `a member is written, written back and removed`() {
        val member = mock<BoardMember>().also { whenever(it.id).thenReturn(4) }
        whenever(repository.existsById(4)).thenReturn(true)
        whenever(repository.findById(4)).thenReturn(Optional.of(member))

        assertThat(service.create(member)).isSameAs(member)
        assertThat(service.update(member)).isSameAs(member)
        service.deleteById(4)

        verify(manager, times(2)).refresh(member)
        verify(repository).delete(member)
    }

    @Test
    fun `an edit or a removal of a member who is not there is refused`() {
        val gone = mock<BoardMember>().also { whenever(it.id).thenReturn(5) }
        whenever(repository.findById(5)).thenReturn(Optional.empty())

        assertThatThrownBy { service.update(gone) }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { service.deleteById(5) }.isInstanceOf(BoardMemberNotFoundException::class.java)
    }
}
