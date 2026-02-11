package net.blueshell.api.domain.membership.persistence.filter

import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

class MembershipFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var from: LocalDate? = null

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    var to: LocalDate? = null
}
