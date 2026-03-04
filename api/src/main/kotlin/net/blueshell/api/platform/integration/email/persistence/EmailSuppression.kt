package net.blueshell.api.platform.integration.email.persistence

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "email_suppressions")
class EmailSuppression(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(nullable = false, unique = true)
    var email: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var reason: SuppressionReason = SuppressionReason.HARD_BOUNCE,

    @Column(name = "first_seen_at", nullable = false)
    var firstSeenAt: Instant = Instant.now(),

    @Column(name = "last_seen_at", nullable = false)
    var lastSeenAt: Instant = Instant.now(),

    @Column(name = "bounce_count", nullable = false)
    var bounceCount: Int = 1,
)

enum class SuppressionReason {
    HARD_BOUNCE,
    COMPLAINT,
    SOFT_BOUNCE_THRESHOLD,
}
