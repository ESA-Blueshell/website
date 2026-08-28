package net.blueshell.api.event.web

import java.time.Instant

interface HasSignUpDeadline {
    val signUpDeadline: Instant?
    val endTime: Instant?
}
