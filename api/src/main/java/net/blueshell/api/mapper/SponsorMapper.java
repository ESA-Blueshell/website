package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.SponsorDTO;
import net.blueshell.api.model.Sponsor;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public abstract class SponsorMapper extends BaseMapper<Sponsor, SponsorDTO> {

    @BeanMapping(ignoreByDefault = true)
    public abstract Sponsor fromDTO(SponsorDTO dto, @MappingTarget Sponsor sponsor);

    @BeanMapping(ignoreByDefault = true)
    public abstract SponsorDTO toDTO(Sponsor sponsor);
}
