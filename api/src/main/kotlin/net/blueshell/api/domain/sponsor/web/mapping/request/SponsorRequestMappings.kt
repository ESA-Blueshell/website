package net.blueshell.api.domain.sponsor.web.mapping.request

import net.blueshell.api.domain.sponsor.command.CreateSponsorCommand
import net.blueshell.api.domain.sponsor.command.UpdateSponsorCommand
import net.blueshell.api.domain.sponsor.web.dto.request.CreateSponsorRequest
import net.blueshell.api.domain.sponsor.web.dto.request.UpdateSponsorRequest

fun CreateSponsorRequest.asCommand(): CreateSponsorCommand =
    CreateSponsorCommand(
        name = this.name!!,
        description = this.description!!,
    )

fun UpdateSponsorRequest.asCommand(id: Long): UpdateSponsorCommand =
    UpdateSponsorCommand(
        id = id,
        name = this.name!!,
        description = this.description!!,
        version = this.version!!,
    )
