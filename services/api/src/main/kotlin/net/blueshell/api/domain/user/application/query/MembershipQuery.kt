package net.blueshell.api.domain.user.application.query

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

/**
 * Query object for filtering memberships in dynamic searches.
 * Used in the application layer for building JPA Specifications.
 */
data class MembershipQuery(
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var from: LocalDate? = null,

    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var to: LocalDate? = null
)
