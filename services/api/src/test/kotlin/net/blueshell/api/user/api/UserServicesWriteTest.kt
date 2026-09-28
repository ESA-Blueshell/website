package net.blueshell.api.user.api

import jakarta.persistence.EntityManager
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.user.domain.AddressService
import net.blueshell.api.user.persistence.Address
import net.blueshell.api.user.persistence.AddressRepository
import net.blueshell.api.user.persistence.MemberProfile
import net.blueshell.api.user.persistence.MemberProfileRepository
import net.blueshell.api.user.persistence.MemberRepository
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.User
import net.blueshell.api.user.persistence.UserRepository
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

/** The reads and writes the user services took over from BaseModelService. */
class UserServicesWriteTest {
    private val manager = mock<EntityManager>()

    private fun <S : Any> S.withEntityManager(): S =
        apply {
            javaClass
                .getDeclaredField("em")
                .apply { isAccessible = true }
                .set(this, manager)
        }

    @Test
    fun `an account is read, listed, checked for and written back`() {
        val user = mock<User>().also { whenever(it.id).thenReturn(3) }
        val repository =
            mock<UserRepository> {
                on { saveAndFlush(any<User>()) } doAnswer { it.getArgument(0) }
            }
        whenever(repository.findById(4)).thenReturn(Optional.empty())
        whenever(repository.findAll()).thenReturn(mutableListOf(user))
        whenever(repository.existsById(3)).thenReturn(true)
        whenever(repository.findById(3)).thenReturn(Optional.of(user))
        val service = UserService(repository, mock(), mock<TrackedEventPublisher>(), mock()).withEntityManager()

        assertThat(service.findAll()).containsExactly(user)
        assertThat(service.existsById(3)).isTrue()
        assertThatThrownBy { service.findById(4) }.isInstanceOf(ResponseStatusException::class.java)
        service.create(user)
        service.update(user)

        verify(manager, times(2)).refresh(user)
        assertThat(service.findById(3)).isSameAs(user)
    }

    @Test
    fun `an edit to an account the database no longer has is refused before anything is written`() {
        val user = mock<User>().also { whenever(it.id).thenReturn(5) }
        val repository = mock<UserRepository>()
        val service = UserService(repository, mock(), mock<TrackedEventPublisher>(), mock()).withEntityManager()

        assertThatThrownBy { service.update(user) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a membership is written back and removed, by itself or by its id`() {
        val membership = mock<Membership>().also { whenever(it.id).thenReturn(6) }
        val repository =
            mock<MemberRepository> {
                on { saveAndFlush(any<Membership>()) } doAnswer { it.getArgument(0) }
            }
        whenever(repository.findById(6)).thenReturn(Optional.of(membership))
        whenever(repository.findById(7)).thenReturn(Optional.empty())
        whenever(repository.existsById(6)).thenReturn(true)
        val service = MembershipService(repository, mock<TrackedEventPublisher>(), mock()).withEntityManager()

        service.create(membership)
        service.update(membership)
        service.delete(membership)
        service.deleteById(6)

        verify(manager, times(2)).refresh(membership)
        verify(repository, times(2)).delete(membership)
        assertThatThrownBy { service.findById(7) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a profile and an address are read and written back`() {
        val profile = mock<MemberProfile>().also { whenever(it.id).thenReturn(1) }
        val profiles = mock<MemberProfileRepository> { on { saveAndFlush(profile) } doAnswer { it.getArgument(0) } }
        whenever(profiles.findById(1)).thenReturn(Optional.of(profile))
        whenever(profiles.findById(9)).thenReturn(Optional.empty())
        whenever(profiles.existsById(1)).thenReturn(true)
        val address = mock<Address>().also { whenever(it.id).thenReturn(2) }
        val addresses = mock<AddressRepository> { on { saveAndFlush(address) } doAnswer { it.getArgument(0) } }
        whenever(addresses.findAll()).thenReturn(mutableListOf(address))
        whenever(addresses.findById(2)).thenReturn(Optional.of(address))
        whenever(addresses.findById(3)).thenReturn(Optional.empty())
        whenever(addresses.existsById(2)).thenReturn(true)
        val profileService = MemberProfileService(profiles).withEntityManager()
        val addressService = AddressService(addresses).withEntityManager()

        assertThat(profileService.findById(1)).isSameAs(profile)
        assertThat(profileService.update(profile)).isSameAs(profile)
        assertThat(addressService.findAll()).containsExactly(address)
        assertThat(addressService.update(address)).isSameAs(address)
        assertThat(addressService.findById(2)).isSameAs(address)
        assertThatThrownBy { addressService.findById(3) }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { profileService.findById(9) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
