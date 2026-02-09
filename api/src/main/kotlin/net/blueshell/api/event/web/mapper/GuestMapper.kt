package net.blueshell.api.event.web.mapper

import io.mcarle.konvert.api.Konverter
import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
import net.blueshell.api.event.web.dto.GuestDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.event.persistence.Guest
import org.springframework.stereotype.Component

@Konverter
interface GuestKonverter {
    fun toDTO(guest: Guest): GuestDTO

    fun fromDTO(dto: GuestDTO): Guest
}

@Component
class GuestMapper : BaseMapper<Guest, GuestDTO>() {
    private val konverter = konverter<GuestKonverter>()

    override fun fromDTO(dto: GuestDTO): Guest = fromDTO(dto, Guest())

    fun fromDTO(dto: GuestDTO, entity: Guest): Guest {
        val mapped = konverter.fromDTO(dto)
        mapped.name?.let { entity.name = it }
        mapped.discord?.let { entity.discord = it }
        mapped.email?.let { entity.email = it }
        mapped.phoneNumber?.let { entity.phoneNumber = it }
        dto.version?.let { entity.version = it }

        if (entity.accessToken == null) {
            entity.accessToken = randomCapitalString(30)
        }

        return entity
    }

    override fun toDTO(entity: Guest): GuestDTO = konverter.toDTO(entity)
}

fun Guest.asDTO(mapper: GuestMapper): GuestDTO = mapper.toDTO(this)

fun GuestDTO.asEntity(mapper: GuestMapper): Guest = mapper.fromDTO(this)
