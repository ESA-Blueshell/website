package net.blueshell.api.domain.event.application.validation

import net.blueshell.api.domain.event.web.dto.EventSignUpDTO

interface EventSignUpCandidate {
    val dto: EventSignUpDTO
}
