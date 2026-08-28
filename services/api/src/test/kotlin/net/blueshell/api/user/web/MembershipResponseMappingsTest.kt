package net.blueshell.api.user.web

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.user.persistence.Membership
import net.blueshell.api.shared.enums.MemberType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

class MembershipResponseMappingsTest {

    @Test
    fun `asResponse maps all fields correctly`() {
        val userId = 42L
        val id = 7L
        val memberType = MemberType.REGULAR
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 12, 31)
        val incasso = true
        val version = 3L
        val createdAt = Instant.parse("2024-01-01T00:00:00Z")
        val updatedAt = Instant.parse("2024-06-01T00:00:00Z")

        val membership = mockk<Membership> {
            every { this@mockk.userId } returns userId
            every { this@mockk.id } returns id
            every { this@mockk.memberType } returns memberType
            every { this@mockk.startDate } returns startDate
            every { this@mockk.endDate } returns endDate
            every { this@mockk.incasso } returns incasso
            every { this@mockk.version } returns version
            every { this@mockk.createdAt } returns createdAt
            every { this@mockk.updatedAt } returns updatedAt
        }

        val response = membership.asResponse()

        assertThat(response.userId).isEqualTo(userId)
        assertThat(response.id).isEqualTo(id)
        assertThat(response.memberType).isEqualTo(memberType)
        assertThat(response.startDate).isEqualTo(startDate)
        assertThat(response.endDate).isEqualTo(endDate)
        assertThat(response.incasso).isEqualTo(incasso)
        assertThat(response.version).isEqualTo(version)
        assertThat(response.createdAt).isEqualTo(createdAt)
        assertThat(response.updatedAt).isEqualTo(updatedAt)
    }

    @Test
    fun `asResponse maps null endDate`() {
        val membership = mockk<Membership> {
            every { userId } returns 1L
            every { id } returns 2L
            every { memberType } returns MemberType.ALUMNI
            every { startDate } returns LocalDate.of(2023, 1, 1)
            every { endDate } returns null
            every { incasso } returns false
            every { version } returns 0L
            every { createdAt } returns Instant.now()
            every { updatedAt } returns Instant.now()
        }

        val response = membership.asResponse()

        assertThat(response.endDate).isNull()
    }
}
