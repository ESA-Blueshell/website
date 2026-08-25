package net.blueshell.api.domain.user.application

import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.UserRepository
import net.blueshell.api.domain.user.application.query.UserQuery
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.security.CurrentUser
import net.blueshell.api.shared.security.CurrentUserProvider
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.security.crypto.password.PasswordEncoder

/**
 * A listing without an `ORDER BY` comes back in whatever order the query plan produced. That
 * is stable enough to look deliberate locally and free to change when the predicates or the
 * indexes do, so the service names an order when the caller does not.
 */
class UserQueryOrderTest {

    private val repository = mock<UserRepository>()
    private val currentUserProvider = mock<CurrentUserProvider>()
    private val service = UserService(
        repository,
        mock<PasswordEncoder>(),
        mock<TrackedEventPublisher>(),
        currentUserProvider,
    )

    private fun capturePageable(requested: Pageable): Pageable {
        whenever(currentUserProvider.currentUser()).thenReturn(CurrentUser(1L, setOf(Role.ADMIN), null))
        whenever(repository.findAll(any<Specification<User>>(), any<Pageable>()))
            .thenReturn(PageImpl(emptyList<User>()) as Page<User>)

        service.findByQuery(UserQuery(), requested)

        val captor = argumentCaptor<Pageable>()
        verify(repository).findAll(any<Specification<User>>(), captor.capture())
        return captor.firstValue
    }

    @Test
    fun `an unsorted unpaged request is ordered by id`() {
        val used = capturePageable(Pageable.unpaged())

        assertThat(used.sort).isEqualTo(Sort.by(Sort.Direction.ASC, "id"))
    }

    @Test
    fun `an unsorted page keeps its page and size and gains the order`() {
        val used = capturePageable(PageRequest.of(2, 25))

        assertThat(used.pageNumber).isEqualTo(2)
        assertThat(used.pageSize).isEqualTo(25)
        assertThat(used.sort).isEqualTo(Sort.by(Sort.Direction.ASC, "id"))
    }

    @Test
    fun `an order the caller asked for is left alone`() {
        val asked = Sort.by(Sort.Direction.DESC, "username")

        val used = capturePageable(PageRequest.of(0, 10, asked))

        assertThat(used.sort).isEqualTo(asked)
    }
}
