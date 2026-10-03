package net.blueshell.api.board.api

import jakarta.persistence.EntityManager
import net.blueshell.api.board.domain.BoardMemberNotFoundException
import net.blueshell.api.board.persistence.BoardMember
import net.blueshell.api.board.persistence.BoardMemberRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class BoardMemberServiceTest {
    private val manager = mock<EntityManager>()
    private val repository =
        mock<BoardMemberRepository> { on { saveAndFlush(any<BoardMember>()) } doAnswer { it.getArgument(0) } }
    private val events = mock<TrackedEventPublisher>()
    private val service =
        BoardMemberService(repository, events).also {
            BoardMemberService::class.java
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(it, manager)
        }

    @Test
    fun `a member is written, written back and removed`() {
        val member = Entities.boardMember(id = 4)
        whenever(repository.existsById(4)).thenReturn(true)
        whenever(repository.findById(4)).thenReturn(Optional.of(member))

        assertThat(service.create(member)).isSameAs(member)
        assertThat(service.update(member)).isSameAs(member)
        service.deleteById(4)

        verify(manager, times(2)).refresh(member)
        verify(repository).delete(member)
        verifyNoInteractions(events)
    }

    @Test
    fun `a change to the place of somebody with an account says whose it was`() {
        val member = Entities.boardMember(id = 6).apply { user = Entities.user(id = 9L) }
        whenever(repository.findById(6)).thenReturn(Optional.of(member))

        service.create(member)
        service.deleteById(6)

        val published = argumentCaptor<(Actor) -> Any>()
        verify(events, times(2)).publish(published.capture())
        assertThat(published.allValues.map { it(Actor.system()) }).containsOnly(BoardMembershipChanged(9L, Actor.system()))
    }

    @Test
    fun `an edit or a removal of a member who is not there is refused`() {
        val gone = Entities.boardMember(id = 5)
        whenever(repository.findById(5)).thenReturn(Optional.empty())

        assertThatThrownBy { service.update(gone) }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { service.deleteById(5) }.isInstanceOf(BoardMemberNotFoundException::class.java)
    }

    @Test
    fun `names who sits on a board not yet in office, and who serves on a day`() {
        val day = java.time.LocalDate.parse("2026-09-01")
        org.mockito.kotlin
            .whenever(repository.findUserIdsOnBoardsStartingAfter(day))
            .thenReturn(listOf(3L, 3L, 4L))
        org.mockito.kotlin
            .whenever(repository.findUserIdsServingOn(day))
            .thenReturn(listOf(1L))

        org.assertj.core.api.Assertions
            .assertThat(service.candidatesOn(day))
            .containsExactlyInAnyOrder(3L, 4L)
        org.assertj.core.api.Assertions
            .assertThat(service.servingOn(day))
            .containsExactly(1L)
    }
}
