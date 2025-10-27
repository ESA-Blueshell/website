package net.blueshell.api.mapper.contribution;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.contribution.ContributionDTO;
import net.blueshell.api.model.contribution.Contribution;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class ContributionMapper extends BaseMapper<Contribution, ContributionDTO> {
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionDTO toDTO(Contribution contribution);

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract Contribution fromDTO(ContributionDTO dto, @MappingTarget Contribution contribution);
}
