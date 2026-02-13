package net.blueshell.api.domain.event.command

import net.blueshell.api.domain.event.web.dto.EventSignUpDTO

interface EventSignUpCandidate {
    val dto: EventSignUpDTO
}
