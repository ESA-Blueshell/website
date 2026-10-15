package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.shared.enums.Role
import net.blueshell.api.shared.model.SignupOutcome
import net.blueshell.api.shared.security.UserPrincipal
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.api.MembershipService
import net.blueshell.api.user.domain.MembershipUseCases
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.user.persistence.MembershipType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.Instant
import java.time.LocalDate

class MembershipControllerTest {
    private val useCases = mock<MembershipUseCases>()
    private val service = mock<MembershipService>()
    private val controller = MembershipController(service, useCases)

    private val alumniSince = LocalDate.of(2025, 9, 1)

    private fun held(): Membership =
        Entities.membership(id = 9, startDate = LocalDate.of(2022, 9, 1)).apply {
            memberType = MemberType.ALUMNI
            // Out of order, as a type added after the load is.
            types += MembershipType(this, MemberType.ALUMNI, alumniSince).apply { id = 2 }
            types += MembershipType(this, MemberType.REGULAR, startDate).apply { id = 1 }
            createdAt = Instant.EPOCH
            updatedAt = Instant.EPOCH
        }

    @Test
    fun `applies for the signed-in account with the acceptance the request carries`() {
        val principal = UserPrincipal(7, "guest", "h", true, setOf(Role.GUEST), 3, null)
        whenever(useCases.apply(7, conditionsAccepted = true))
            .thenReturn(SignupOutcome(emailConfirmed = true, membershipStarted = true))

        val response = controller.createMembership(MembershipApplicationRequest(conditionsAccepted = true), principal)

        assertThat(response.emailConfirmed).isTrue()
        assertThat(response.membershipStarted).isTrue()
    }

    @Test
    fun `records, corrects and removes a type, answering with every type the membership holds`() {
        val request = MembershipTypeRequest(type = MemberType.ALUMNI, since = alumniSince)
        whenever(useCases.recordType(9, MemberType.ALUMNI, alumniSince)).thenReturn(held())
        whenever(useCases.correctType(9, 2, MemberType.ALUMNI, alumniSince)).thenReturn(held())
        whenever(useCases.removeType(9, 2)).thenReturn(held())

        val recorded = controller.recordMembershipType(9, request)
        controller.correctMembershipType(9, 2, request)
        controller.removeMembershipType(9, 2)

        assertThat(recorded.memberType).isEqualTo(MemberType.ALUMNI)
        assertThat(recorded.types).containsExactly(
            MembershipTypeResponse(id = 1, type = MemberType.REGULAR, since = LocalDate.of(2022, 9, 1)),
            MembershipTypeResponse(id = 2, type = MemberType.ALUMNI, since = alumniSince),
        )
    }

    @Test
    fun `reads the reader's own memberships`() {
        val principal = UserPrincipal(7, "member", "h", true, setOf(Role.MEMBER), 3, null)
        whenever(service.findByUserId(7)).thenReturn(mutableListOf(held()))

        assertThat(controller.findOwnMemberships(principal).map { it.id }).containsExactly(9L)
    }
}
