package net.blueshell.api.controller.filter

import lombok.Data
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDate

@Data
class MembershipFilter {
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private val from: LocalDate? = null

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private val to: LocalDate? = null
}