package net.blueshell.api.user.api

import jakarta.persistence.criteria.CriteriaBuilder
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Path
import jakarta.persistence.criteria.Root
import net.blueshell.api.shared.model.SoftDelete
import net.blueshell.api.user.domain.AddressLifecycleQuery
import net.blueshell.api.user.domain.ProfileLifecycleQuery
import net.blueshell.api.user.persistence.AddressLifecycle
import net.blueshell.api.user.persistence.AddressLifecycleRepo
import net.blueshell.api.user.persistence.AddressLifecycleSpecs
import net.blueshell.api.user.persistence.AddressRepository
import net.blueshell.api.user.persistence.DeletedUser
import net.blueshell.api.user.persistence.DeletedUserRepository
import net.blueshell.api.user.persistence.ProfileLifecycle
import net.blueshell.api.user.persistence.ProfileLifecycleRepo
import net.blueshell.api.user.persistence.ProfileLifecycleSpecs
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.data.jpa.domain.Specification
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional

/** A restore brings the profile and address back to the live sentinel, and the queries find them by it. */
class UserErasureRestoreLifecycleTest {
    private val user =
        User(
            username = "nelly",
            email = "nelly@example.com",
            password = "encoded",
            initials = "N",
            firstName = "Nelly",
            lastName = "B",
            newsletter = false,
        ).apply { id = 7 }

    @Test
    fun `a restore sets the profile and the address live again`() {
        val deletedUsers = mock<DeletedUserRepository>()
        val snapshot = DeletedUser.fromUser(user, Instant.now(), Instant.now().plus(1, ChronoUnit.DAYS)).apply { addressId = 5 }
        whenever(deletedUsers.findById(7)).thenReturn(Optional.of(snapshot))
        val gone = Instant.parse("2026-01-01T00:00:00Z")
        val profile = ProfileLifecycle(id = 7, deletedAt = gone)
        val address = AddressLifecycle(id = 5, deletedAt = gone)
        val profiles =
            mock<ProfileLifecycleRepo>().also {
                whenever(it.findOne(any<Specification<ProfileLifecycle>>())).thenReturn(Optional.of(profile))
            }
        val addresses =
            mock<AddressLifecycleRepo>().also {
                whenever(it.findOne(any<Specification<AddressLifecycle>>())).thenReturn(Optional.of(address))
            }
        val userRepository = mock<UserRepository>()
        whenever(userRepository.saveAndFlush(any<User>())).thenAnswer { it.arguments[0] }
        val users = mock<UserService> { on { findById(7) } doReturn user }
        val addressRows = mock<AddressRepository> { on { findById(5) } doReturn Optional.empty() }

        UserErasureService(users, userRepository, deletedUsers, profiles, addresses, addressRows, mock(), mock(), 90)
            .restoreDeletedUser(7)

        assertThat(profile.deletedAt).isEqualTo(SoftDelete.LIVE_INSTANT)
        assertThat(address.deletedAt).isEqualTo(SoftDelete.LIVE_INSTANT)
        verify(profiles).saveAndFlush(profile)
        verify(addresses).saveAndFlush(address)
    }

    @Test
    fun `a new lifecycle row is live, and the queries ask for live and deleted rows by the sentinel`() {
        val deletedAt = mock<Path<Instant>>()
        val root = mock<Root<Any>> { on { get<Instant>("deletedAt") } doReturn deletedAt }
        val cb = mock<CriteriaBuilder>()
        val query = mock<CriteriaQuery<*>>()

        @Suppress("UNCHECKED_CAST")
        fun <T : Any> ask(spec: Specification<T>) = spec.toPredicate(root as Root<T>, query, cb)

        assertThat(AddressLifecycle().deletedAt).isEqualTo(SoftDelete.LIVE_INSTANT)
        assertThat(ProfileLifecycle().deletedAt).isEqualTo(SoftDelete.LIVE_INSTANT)
        ask(AddressLifecycleSpecs.fromQuery(AddressLifecycleQuery(softDeleted = true)))
        ask(AddressLifecycleSpecs.fromQuery(AddressLifecycleQuery(softDeleted = false)))
        ask(ProfileLifecycleSpecs.fromQuery(ProfileLifecycleQuery(softDeleted = true)))
        ask(ProfileLifecycleSpecs.fromQuery(ProfileLifecycleQuery(softDeleted = false)))

        verify(cb, org.mockito.kotlin.times(2)).notEqual(deletedAt, SoftDelete.LIVE_INSTANT)
        verify(cb, org.mockito.kotlin.times(2)).equal(deletedAt, SoftDelete.LIVE_INSTANT)
    }
}
