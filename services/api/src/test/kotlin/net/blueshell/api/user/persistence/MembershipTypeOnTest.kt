package net.blueshell.api.user.persistence

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.testsupport.Entities
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MembershipTypeOnTest {
    private val start = LocalDate.of(2022, 9, 1)
    private val alumniSince = LocalDate.of(2025, 9, 1)

    private fun membership() =
        Membership(user = Entities.user(), startDate = start, memberType = MemberType.REGULAR).apply {
            recordType(MemberType.REGULAR, start)
        }

    @Test
    fun `a membership is the type it started as until another type is recorded`() {
        val membership = membership()

        assertThat(membership.typeOn(LocalDate.of(2024, 1, 1))).isEqualTo(MemberType.REGULAR)
    }

    @Test
    fun `a recorded type holds from its own day on`() {
        val membership = membership().apply { recordType(MemberType.ALUMNI, alumniSince) }

        assertThat(membership.typeOn(alumniSince.minusDays(1))).isEqualTo(MemberType.REGULAR)
        assertThat(membership.typeOn(alumniSince)).isEqualTo(MemberType.ALUMNI)
        assertThat(membership.typeOn(LocalDate.of(2026, 3, 1))).isEqualTo(MemberType.ALUMNI)
    }

    @Test
    fun `types recorded out of order are read by their dates`() {
        val membership =
            membership().apply {
                recordType(MemberType.HONORARY, LocalDate.of(2026, 1, 1))
                recordType(MemberType.ALUMNI, alumniSince)
            }

        assertThat(membership.typeOn(LocalDate.of(2025, 12, 31))).isEqualTo(MemberType.ALUMNI)
        assertThat(membership.typeOn(LocalDate.of(2026, 1, 1))).isEqualTo(MemberType.HONORARY)
        assertThat(membership.types.map { it.type })
            .containsExactly(MemberType.REGULAR, MemberType.ALUMNI, MemberType.HONORARY)
    }

    @Test
    fun `a day before the membership reads its first type`() {
        val membership = membership().apply { recordType(MemberType.ALUMNI, alumniSince) }

        assertThat(membership.typeOn(start.minusYears(1))).isEqualTo(MemberType.REGULAR)
    }

    @Test
    fun `each type belongs to its membership, and Hibernate can build one empty`() {
        val membership = membership()

        assertThat(membership.types.single().membership).isSameAs(membership)
        assertThat(MembershipType::class.java.getDeclaredConstructor().newInstance()).isNotNull
    }

    @Test
    fun `the current type is the latest one recorded`() {
        val membership = membership().apply { recordType(MemberType.ALUMNI, alumniSince) }

        assertThat(membership.memberType).isEqualTo(MemberType.ALUMNI)
    }
}
