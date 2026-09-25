package net.blueshell.api.user.api

import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.MemberProfileRepository
import net.blueshell.api.user.persistence.User
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.Optional

class MemberProfileServiceTest {
    private val repository = mock<MemberProfileRepository>()
    private val service = MemberProfileService(repository)

    @Test
    fun `a profile answers the account's own say on the name beside a handle`() {
        val user = User(username = "alice", email = "a@example.com", password = "h", initials = "A", firstName = "Alice", lastName = "Doe")
        val profile = MemberProfile(user = user, bhv = false, ehbo = false)
        whenever(repository.findById(7)).thenReturn(Optional.of(profile))

        service.findById(7).nameOnRosters = true

        assertThat(user.nameOnRosters).isTrue()
        assertThat(profile.nameOnRosters).isTrue()
    }
}
