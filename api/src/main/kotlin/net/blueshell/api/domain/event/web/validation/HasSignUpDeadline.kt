package net.blueshell.api.domain.event.web.validation

import java.time.Instant

interface HasSignUpDeadline {
    val signUpDeadline: Instant?
    val endTime: Instant?
}
