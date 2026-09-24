package net.blueshell.api.user.api

import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.user.domain.ErasureException
import net.blueshell.api.user.persistence.AddressLifecycleRepo
import net.blueshell.api.user.persistence.AddressRepository
import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.DeletedUserRepository
import net.blueshell.api.user.persistence.ProfileLifecycleRepo
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/** What erasure does with the linked Discord member: forgets it, keeps it for a restore, and gives it back. */
class UserErasureDiscordTest {
    private val user =
        User(
            username = "nelly",
            email = "nelly@example.com",
            password = "encoded",
            initials = "N",
            firstName = "Nelly",
            lastName = "B",
            discord = "Nelly B",
            newsletter = false,
        ).apply {
            id = 7
            discordId = "1144058844004233369"
        }
    private val users: UserService = mock { on { findById(7) } doReturn user }
    private val userRepository: UserRepository = mock()
    private val deletedUsers: DeletedUserRepository = mock()
    private val trackedEvents: TrackedEventPublisher = mock()

    private fun erasure() =
        UserErasureService(
            users,
            userRepository,
            deletedUsers,
            mock<ProfileLifecycleRepo>(),
            mock<AddressLifecycleRepo>(),
            mock<AddressRepository>(),
            trackedEvents,
            90,
        )

    private fun snapshot() =
        DeletedUser.fromUser(user, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS))

    @Test
    fun `forgets the linked member on the account, and keeps it in the snapshot`() {
        erasure().deleteUser(7)

        val kept = argumentCaptor<DeletedUser>()
        verify(deletedUsers).save(kept.capture())
        assertThat(kept.firstValue.discordId).isEqualTo("1144058844004233369")
        assertThat(user.discordId).isNull()
        assertThat(user.discord).isNull()
    }

    @Test
    fun `gives the linked member back on a restore`() {
        val kept = snapshot()
        user.discordId = null
        whenever(deletedUsers.findById(7)).thenReturn(Optional.of(kept))
        whenever(userRepository.saveAndFlush(any<User>())).thenAnswer { it.arguments[0] }

        erasure().restoreDeletedUser(7)

        assertThat(user.discordId).isEqualTo("1144058844004233369")
    }

    @Test
    fun `refuses a restore where another account has linked the member since`() {
        whenever(deletedUsers.findById(7)).thenReturn(Optional.of(snapshot()))
        whenever(userRepository.existsByDiscordId("1144058844004233369")).thenReturn(true)

        assertThatThrownBy { erasure().restoreDeletedUser(7) }
            .isInstanceOf(ErasureException.Conflict::class.java)
            .hasMessageContaining("discord account is already linked")
    }
}
