package net.blueshell.api.user.persistence

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.testsupport.UserTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.domain.PageRequest

/**
 * Real-MariaDB checks for the keyset query backing the cohort all-users
 * reconcile: ascending by id, strictly after the cursor, honouring the limit.
 */
@SpringBootTest
class UserRepositoryPagingIT : UserTestSupport() {

    @Autowired private lateinit var users: UserRepository

    @Test
    fun `findActiveIdsAfter walks active ids ascending, after the cursor, within the limit`() {
        val a = createUserWithRole(Role.MEMBER).id!!
        val b = createUserWithRole(Role.MEMBER).id!!
        val c = createUserWithRole(Role.MEMBER).id!!

        // Limit caps the page; the single id after `a` is the next one, `b`.
        assertThat(users.findActiveIdsAfter(a, PageRequest.of(0, 1))).containsExactly(b)

        // Strictly-after semantics + ascending order.
        val afterB = users.findActiveIdsAfter(b, PageRequest.of(0, 100))
        assertThat(afterB).contains(c).doesNotContain(a, b)

        val afterA = users.findActiveIdsAfter(a, PageRequest.of(0, 100))
        assertThat(afterA).isSorted()
        assertThat(afterA).containsSubsequence(b, c)
    }
}
