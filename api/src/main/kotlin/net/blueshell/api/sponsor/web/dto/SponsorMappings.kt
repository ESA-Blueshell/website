package net.blueshell.api.sponsor.web.dto

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.sponsor.persistence.Sponsor

@Konverter
interface SponsorKonverter {
    fun toDTO(sponsor: Sponsor): SponsorDTO

    fun fromDTO(dto: SponsorDTO): Sponsor
}

private val sponsorKonverter = Konverter.get<SponsorKonverter>()

fun SponsorDTO.asEntity(sponsor: Sponsor = Sponsor()): Sponsor {
    val mapped = sponsorKonverter.fromDTO(this)
    sponsor.name = mapped.name
    sponsor.description = mapped.description
    sponsor.version = version
    return sponsor
}
