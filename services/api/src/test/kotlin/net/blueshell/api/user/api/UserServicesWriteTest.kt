package net.blueshell.api.user.api

import jakarta.persistence.EntityManager
import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.event.TrackedEventPublisher
import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.domain.AddressService
import net.blueshell.api.user.persistence.AddressRepository
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
import java.time.LocalDate
import java.util.Optional

/** The reads and writes the user services make against their repositories. */
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
        val user = Entities.user(id = 3)
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
        val user = Entities.user(id = 5)
        val repository = mock<UserRepository>()
        val service = UserService(repository, mock(), mock<TrackedEventPublisher>(), mock()).withEntityManager()

        assertThatThrownBy { service.update(user) }.isInstanceOf(ResponseStatusException::class.java)
    }

    @Test
    fun `a membership is written back and removed, by itself or by its id`() {
        val membership = Entities.membership(id = 6)
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
    fun `a new membership waits for its first contribution unless it is honorary, and the payment makes it active`() {
        val repository =
            mock<MemberRepository> {
                on { saveAndFlush(any<Membership>()) } doAnswer { it.getArgument(0) }
            }
        whenever(repository.existsById(any())).thenReturn(true)
        val service = MembershipService(repository, mock<TrackedEventPublisher>(), mock()).withEntityManager()
        val today = LocalDate.now()

        val regular = service.create(Entities.membership(id = 1, startDate = today, activatedOn = null))
        assertThat(regular.isPending).isTrue()
        val honorary = service.create(Entities.membership(id = 2, activatedOn = null).apply { memberType = MemberType.HONORARY })
        assertThat(honorary.activatedOn).isEqualTo(honorary.startDate)
        val madeHonorary = service.update(Entities.membership(id = 3, activatedOn = null).apply { memberType = MemberType.HONORARY })
        assertThat(madeHonorary.activatedOn).isEqualTo(today)

        val ended = Entities.membership(id = 4, endDate = today, activatedOn = null)
        whenever(repository.findByUser_Id(9)).thenReturn(mutableListOf(regular, ended))
        service.activatePending(9)
        assertThat(regular.activatedOn).isEqualTo(today)
        assertThat(ended.activatedOn).isNull()
        verify(repository).saveAll(listOf(regular))
        service.activatePending(9)
        verify(repository, times(1)).saveAll(any<List<Membership>>())
    }

    @Test
    fun `every change tells whether the user still holds an active membership`() {
        val membership = Entities.membership(id = 6, user = Entities.user(id = 9))
        val repository =
            mock<MemberRepository> {
                on { saveAndFlush(any<Membership>()) } doAnswer { it.getArgument(0) }
            }
        whenever(repository.findById(6)).thenReturn(Optional.of(membership))
        whenever(repository.existsById(6)).thenReturn(true)
        whenever(repository.restoreById(6)).thenReturn(1)
        whenever(repository.existsByUser_IdAndEndDateIsNullAndActivatedOnIsNotNull(9)).thenReturn(true)
        val told = mutableListOf<MembershipChanged>()
        val events =
            mock<TrackedEventPublisher> {
                on { publish(any()) } doAnswer {
                    told.add(it.getArgument<(Actor) -> Any>(0)(Actor.system()) as MembershipChanged)
                    Unit
                }
            }
        val service = MembershipService(repository, events, mock()).withEntityManager()

        service.create(membership)
        service.update(membership)
        service.delete(membership)
        service.deleteById(6)
        service.restore(membership)

        assertThat(told.map { it.active }).containsOnly(true).hasSize(5)
        assertThat(service.existsActiveMembershipByUserId(9)).isTrue()
        whenever(repository.existsByUser_IdAndEndDateIsNull(9)).thenReturn(true)
        assertThat(service.existsRunningMembershipByUserId(9)).isTrue()
    }

    @Test
    fun `a profile and an address are read and written back`() {
        val profile = Entities.memberProfile(id = 1)
        val profiles = mock<MemberProfileRepository> { on { saveAndFlush(profile) } doAnswer { it.getArgument(0) } }
        whenever(profiles.findById(1)).thenReturn(Optional.of(profile))
        whenever(profiles.findById(9)).thenReturn(Optional.empty())
        whenever(profiles.existsById(1)).thenReturn(true)
        val address = Entities.address(id = 2)
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

    @Test
    fun `an edit to a membership, a profile or an address the database no longer has is refused`() {
        val membership = Entities.membership(id = 1)
        val profile = Entities.memberProfile(id = 2)
        val address = Entities.address(id = 3)
        val memberships = MembershipService(mock<MemberRepository>(), mock<TrackedEventPublisher>(), mock())
        val profiles = MemberProfileService(mock<MemberProfileRepository>())
        val addresses = AddressService(mock<AddressRepository>())

        assertThatThrownBy { memberships.update(membership) }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { profiles.update(profile) }.isInstanceOf(ResponseStatusException::class.java)
        assertThatThrownBy { addresses.update(address) }.isInstanceOf(ResponseStatusException::class.java)
    }
}
