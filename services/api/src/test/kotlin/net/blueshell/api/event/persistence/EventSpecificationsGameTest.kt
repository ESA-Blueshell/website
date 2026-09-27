package net.blueshell.api.event.persistence

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Predicate
import jakarta.persistence.criteria.Root
import net.blueshell.api.event.domain.EventQuery
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify

class EventSpecificationsGameTest {
    private val named = mock<Predicate>()
    private val codes = mock<Path<Collection<String>>>()
    private val root = mock<Root<Event>> { on { get<Collection<String>>("gameCodes") } doReturn codes }
    private val cb =
        mock<CriteriaBuilder> {
            on { isMember("CHESS", codes) } doReturn named
            on { conjunction() } doReturn mock()
            on { and(anyOrNull<Predicate>(), anyOrNull<Predicate>()) } doReturn mock()
        }
    private val board = CurrentUser(1L, setOf(Role.BOARD), null)

    @Test
    fun `asks for the events that name a game, by its trimmed code, and nothing of games where none is asked`() {
        EventSpecifications.fromFilter(EventQuery(gameCode = " CHESS "), board).toPredicate(root, mock<CriteriaQuery<*>>(), cb)
        EventSpecifications.fromFilter(EventQuery(gameCode = " "), board).toPredicate(root, mock<CriteriaQuery<*>>(), cb)
        EventSpecifications.fromFilter(EventQuery(), board).toPredicate(root, mock<CriteriaQuery<*>>(), cb)
        verify(cb, times(1)).isMember(any<String>(), any<Path<Collection<String>>>())
        verify(cb).isMember("CHESS", codes)
        assertThat(EventQuery().gameCode).isNull()
    }
}
