package net.blueshell.api.domain.user.application.validation

import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder.NodeBuilderCustomizableContext
import net.blueshell.api.domain.user.persistence.Membership
import net.blueshell.api.domain.user.persistence.User
import net.blueshell.api.domain.user.persistence.repository.MemberRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.time.LocalDate
import java.util.Optional

class MembershipValidatorTest {

    private val repository = mock<MemberRepository>()
    private val validator = MembershipValidator(repository)
    private val context = mock<ConstraintValidatorContext>()

    @BeforeEach
    fun stubContext() {
        val builder = mock<ConstraintViolationBuilder>()
        val node = mock<NodeBuilderCustomizableContext>()
        whenever(context.buildConstraintViolationWithTemplate(any())).thenReturn(builder)
        whenever(builder.addPropertyNode(any())).thenReturn(node)
        whenever(node.addConstraintViolation()).thenReturn(context)
    }

    private fun candidate(userId: Long?, id: Long?, start: LocalDate?, end: LocalDate?) =
        object : MembershipIntervalCandidate {
            override val candidateUserId = userId
            override val candidateMembershipId = id
            override val candidateStartDate = start
            override val candidateEndDate = end
        }

    private fun existing(id: Long, start: LocalDate, end: LocalDate?): Membership =
        Membership(user = mock<User>(), startDate = start, endDate = end).apply { this.id = id }

    private fun ownedBy(userId: Long): Membership {
        val user = User(
            username = "u$userId",
            email = "u$userId@example.com",
            password = "encoded",
            initials = "U",
            firstName = "U",
            prefix = null,
            lastName = "U",
            phoneNumber = "0612345678",
            discord = "u#0001",
            newsletter = true
        ).apply { id = userId }
        return Membership(user = user, startDate = LocalDate.of(2020, 1, 1))
    }

    private fun others(vararg memberships: Membership) {
        whenever(repository.findByUser_Id(1L)).thenReturn(memberships.toMutableList())
    }

    @Test
    fun `accepts a closed interval that does not overlap`() {
        others(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))
        assertThat(
            validator.isValid(candidate(1L, null, LocalDate.of(2022, 1, 1), LocalDate.of(2023, 1, 1)), context)
        ).isTrue()
    }

    @Test
    fun `rejects startDate equal to endDate`() {
        others()
        assertThat(
            validator.isValid(candidate(1L, null, LocalDate.of(2022, 1, 1), LocalDate.of(2022, 1, 1)), context)
        ).isFalse()
    }

    @Test
    fun `rejects endDate before startDate`() {
        others()
        assertThat(
            validator.isValid(candidate(1L, null, LocalDate.of(2022, 6, 1), LocalDate.of(2022, 1, 1)), context)
        ).isFalse()
    }

    @Test
    fun `rejects a second active membership`() {
        others(existing(2L, LocalDate.of(2020, 1, 1), null))
        assertThat(
            validator.isValid(candidate(1L, null, LocalDate.of(2022, 1, 1), null), context)
        ).isFalse()
    }

    @Test
    fun `rejects overlapping intervals`() {
        others(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))
        assertThat(
            validator.isValid(candidate(1L, null, LocalDate.of(2020, 6, 1), LocalDate.of(2020, 9, 1)), context)
        ).isFalse()
    }

    @Test
    fun `allows touching endpoints because end dates are exclusive`() {
        others(existing(2L, LocalDate.of(2020, 1, 1), LocalDate.of(2021, 1, 1)))
        assertThat(
            validator.isValid(candidate(1L, null, LocalDate.of(2021, 1, 1), LocalDate.of(2022, 1, 1)), context)
        ).isTrue()
    }

    @Test
    fun `excludes the edited membership and resolves the real owner by id`() {
        val self = existing(5L, LocalDate.of(2020, 1, 1), null)
        whenever(repository.findById(5L)).thenReturn(Optional.of(ownedBy(1L)))
        others(self)

        // userId in the candidate is deliberately wrong; the validator must use
        // the membership's real owner (1L) and exclude the row being edited.
        assertThat(
            validator.isValid(candidate(999L, 5L, LocalDate.of(2020, 1, 1), null), context)
        ).isTrue()
    }
}
