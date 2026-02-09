package net.blueshell.api.sponsor.persistence

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.sponsor.web.dto.SponsorDTO
import net.blueshell.api.sponsor.web.dto.SponsorKonverter

private val sponsorKonverter = Konverter.get<SponsorKonverter>()

fun Sponsor.asDto(): SponsorDTO = sponsorKonverter.toDTO(this)
