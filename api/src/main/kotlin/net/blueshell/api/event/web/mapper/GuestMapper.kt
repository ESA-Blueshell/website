package net.blueshell.api.event.web.mapper

import net.blueshell.api.shared.util.MappingUtil.randomCapitalString
import net.blueshell.api.event.web.dto.GuestDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.event.persistence.Guest
import org.springframework.stereotype.Component

@Component
class GuestMapper : BaseMapper<Guest, GuestDTO>() {
    override fun fromDTO(dto: GuestDTO): Guest = fromDTO(dto, Guest())

    fun fromDTO(dto: GuestDTO, entity: Guest): Guest {
        dto.name?.let { entity.name = it }
        dto.discord?.let { entity.discord = it }
        dto.email?.let { entity.email = it }
        dto.phoneNumber?.let { entity.phoneNumber = it }
        dto.version?.let { entity.version = it }

        if (entity.accessToken == null) {
            entity.accessToken = randomCapitalString(30)
        }

        return entity
    }

    override fun toDTO(entity: Guest): GuestDTO {
        return GuestDTO(
            name = entity.name,
            discord = entity.discord,
            email = entity.email,
            phoneNumber = entity.phoneNumber,
            accessToken = entity.accessToken
        ).also { dto ->
            dto.id = entity.id
            dto.version = entity.version
        }
    }
}

fun Guest.asDTO(mapper: GuestMapper): GuestDTO = mapper.toDTO(this)

fun GuestDTO.asEntity(mapper: GuestMapper): Guest = mapper.fromDTO(this)
