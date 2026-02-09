package net.blueshell.api.sponsor.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.sponsor.web.dto.SponsorDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.sponsor.persistence.Sponsor
import org.springframework.stereotype.Component

@Konverter
interface SponsorKonverter {
    fun toDTO(sponsor: Sponsor): SponsorDTO

    fun fromDTO(dto: SponsorDTO): Sponsor
}

@Component
class SponsorMapper : BaseMapper<Sponsor, SponsorDTO>() {
    private val konverter = konverter<SponsorKonverter>()

    override fun fromDTO(dto: SponsorDTO): Sponsor = konverter.fromDTO(dto)

    fun fromDTO(dto: SponsorDTO, sponsor: Sponsor): Sponsor {
        val mapped = konverter.fromDTO(dto)
        sponsor.name = mapped.name
        sponsor.description = mapped.description
        sponsor.version = dto.version
        return sponsor
    }

    override fun toDTO(entity: Sponsor): SponsorDTO = konverter.toDTO(entity)
}

fun Sponsor.asDTO(mapper: SponsorMapper): SponsorDTO = mapper.toDTO(this)

fun SponsorDTO.asEntity(mapper: SponsorMapper): Sponsor = mapper.fromDTO(this)
