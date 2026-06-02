package net.blueshell.api.domain.committee.application

import io.mockk.every
import io.mockk.mockk
import net.blueshell.api.domain.committee.persistence.repository.CommitteeMemberRepository
import net.blueshell.api.shared.event.TrackedEventPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.Date

/**
 * Pure-unit tests for the datetime coercion in
 * [CommitteeMemberService.findMembershipWindowsForUser]. The MariaDB JDBC
 * driver hands `DATETIME(6)` columns back as `java.time.LocalDateTime`
 * in current versions and as `java.sql.Timestamp` in older / pool-wrapped
 * versions; we hit one production user whose row crashed with a
 * `ClassCastException` when the service blindly cast to Timestamp.
 *
 * Each test seeds the repo mock with one of the JDBC return types the
 * driver is known to produce and asserts the resulting Instant matches.
 */
class CommitteeMemberServiceTest {

    private val repository: CommitteeMemberRepository = mockk()
    private val trackedEvents: TrackedEventPublisher = mockk(relaxed = true)
    private val service = CommitteeMemberService(repository, trackedEvents)

    private val joined = Instant.parse("2024-09-01T10:00:00Z")
    private val left = Instant.parse("9999-12-31T23:59:59Z")

    @Test
    fun `maps LocalDateTime values returned by the modern MariaDB driver`() {
        every { repository.findWindowsByUserId(1L) } returns listOf(
            arrayOf<Any>(
                42L,
                LocalDateTime.ofInstant(joined, ZoneOffset.UTC),
                LocalDateTime.ofInstant(left, ZoneOffset.UTC),
            ),
        )

        val windows = service.findMembershipWindowsForUser(1L)

        assertThat(windows).singleElement().satisfies({
            assertThat(it.committeeId).isEqualTo(42L)
            assertThat(it.joinedAt).isEqualTo(joined)
            assertThat(it.leftAt).isEqualTo(left)
        })
    }

    @Test
    fun `maps java sql Timestamp values returned by older JDBC stacks`() {
        every { repository.findWindowsByUserId(1L) } returns listOf(
            arrayOf<Any>(42L, Timestamp.from(joined), Timestamp.from(left)),
        )

        val windows = service.findMembershipWindowsForUser(1L)

        assertThat(windows).singleElement().satisfies({
            assertThat(it.joinedAt).isEqualTo(joined)
            assertThat(it.leftAt).isEqualTo(left)
        })
    }

    @Test
    fun `maps OffsetDateTime and java util Date too`() {
        every { repository.findWindowsByUserId(1L) } returns listOf(
            arrayOf<Any>(42L, OffsetDateTime.ofInstant(joined, ZoneOffset.UTC), Date.from(left)),
        )

        val windows = service.findMembershipWindowsForUser(1L)

        assertThat(windows.first().joinedAt).isEqualTo(joined)
        assertThat(windows.first().leftAt).isEqualTo(left)
    }

    @Test
    fun `coerces numeric id types from BigInteger or Integer to Long`() {
        every { repository.findWindowsByUserId(1L) } returns listOf(
            arrayOf<Any>(
                java.math.BigInteger.valueOf(42L),
                Timestamp.from(joined),
                Timestamp.from(left),
            ),
        )

        assertThat(service.findMembershipWindowsForUser(1L).first().committeeId).isEqualTo(42L)
    }

    @Test
    fun `unexpected datetime type fails fast with a descriptive message`() {
        every { repository.findWindowsByUserId(1L) } returns listOf(
            arrayOf<Any>(42L, "2024-09-01", Timestamp.from(left)),
        )

        assertThatThrownBy { service.findMembershipWindowsForUser(1L) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unexpected datetime value type")
    }

    @Test
    fun `empty result returns empty list`() {
        every { repository.findWindowsByUserId(1L) } returns emptyList()

        assertThat(service.findMembershipWindowsForUser(1L)).isEmpty()
    }
}
