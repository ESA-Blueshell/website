package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.MemberType
import net.blueshell.api.testsupport.Entities
import net.blueshell.api.user.persistence.Membership
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

        val membership =
            Entities.membership(id = id, user = Entities.user(id = userId), startDate = startDate, endDate = endDate).also {
                it.memberType = memberType
                it.incasso = incasso
                it.version = version
                it.createdAt = createdAt
                it.updatedAt = updatedAt
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
        val membership =
            Entities.membership(id = 2L, user = Entities.user(id = 1L), startDate = LocalDate.of(2023, 1, 1)).also {
                it.memberType = MemberType.ALUMNI
                it.createdAt = Instant.now()
                it.updatedAt = Instant.now()
            }

        val response = membership.asResponse()

        assertThat(response.endDate).isNull()
    }

    @Test
    fun `asResponse says a membership waiting for its first contribution is pending`() {
        val start = LocalDate.of(2026, 9, 1)
        val stamped: (Membership) -> Unit = {
            it.createdAt = Instant.now()
            it.updatedAt = Instant.now()
        }
        val pending = Entities.membership(id = 3L, startDate = start, activatedOn = null).also(stamped)
        val active = Entities.membership(id = 4L, startDate = start).also(stamped)

        assertThat(pending.asResponse().pending).isTrue()
        assertThat(pending.asResponse().activatedOn).isNull()
        assertThat(active.asResponse().pending).isFalse()
        assertThat(active.asResponse().activatedOn).isEqualTo(start)
    }
}
