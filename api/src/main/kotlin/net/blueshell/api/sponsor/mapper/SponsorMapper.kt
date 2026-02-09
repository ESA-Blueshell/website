package net.blueshell.api.sponsor.mapper

import net.blueshell.api.sponsor.dto.SponsorDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.sponsor.model.Sponsor
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class SponsorMapper : BaseMapper<Sponsor, SponsorDTO>() {
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "version")
    abstract fun fromDTO(dto: SponsorDTO, @MappingTarget sponsor: Sponsor): Sponsor

    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id")
    @Mapping(target = "name")
    @Mapping(target = "description")
    @Mapping(target = "version")
    abstract override fun toDTO(sponsor: Sponsor): SponsorDTO
}
