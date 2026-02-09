package net.blueshell.api.sponsor.web.mapping

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.sponsor.persistence.Sponsor
import net.blueshell.api.sponsor.web.dto.SponsorDTO

@Konverter
interface SponsorKonverter {
    fun toDTO(sponsor: Sponsor): SponsorDTO

    fun fromDTO(dto: SponsorDTO): Sponsor
}

private val sponsorKonverter = Konverter.get<SponsorKonverter>()

fun SponsorDTO.asEntity(existing: Sponsor? = null): Sponsor {
    val mapped = sponsorKonverter.fromDTO(this)
    existing?.let { current ->
        mapped.assignIdForRef(current.id!!)
        mapped.picture = current.picture
    }
    return mapped
}

fun Sponsor.asDto(): SponsorDTO = sponsorKonverter.toDTO(this)
