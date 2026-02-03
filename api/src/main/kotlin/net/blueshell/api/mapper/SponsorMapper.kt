package net.blueshell.api.mapper

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.SponsorDTO
import net.blueshell.api.model.Sponsor
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class SponsorMapper : BaseMapper<Sponsor, SponsorDTO>() {
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: SponsorDTO, @MappingTarget sponsor: Sponsor): Sponsor

    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(sponsor: Sponsor): SponsorDTO
}
