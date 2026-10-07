package net.blueshell.api.pinger.api

import org.springframework.security.core.Authentication
import org.springframework.security.oauth2.jwt.Jwt

/**
 * Who a report accrues to. The report chain (api ADR-035) authenticates either a member, whose
 * bearer carries their id as its subject, or the always-on SiteCie painter, which is not a user.
 * [key] is the stable identity both the durable tally and the live Valkey row are keyed on.
 */
sealed interface PingerIdentity {
    val key: String
    val memberId: Long?

    data class Member(
        val id: Long,
    ) : PingerIdentity {
        override val key: String get() = "member:$id"
        override val memberId: Long get() = id
    }

    data object Sitecie : PingerIdentity {
        override val key: String get() = "sitecie"
        override val memberId: Long? get() = null
    }

    companion object {
        /**
         * The identity the report chain resolved: a member whose bearer subject is their id, or
         * SiteCie for the service token, which authenticates to no user.
         */
        fun of(authentication: Authentication): PingerIdentity {
            val principal = authentication.principal
            return if (principal is Jwt) Member(requireNotNull(principal.subject).toLong()) else Sitecie
        }
    }
}
