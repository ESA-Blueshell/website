package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.ContributionDTO;
import net.blueshell.api.model.contribution.Contribution;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ContributionMapper extends BaseMapper<Contribution, ContributionDTO> {
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "paid")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionDTO toDTO(Contribution contribution);

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "paid")
    @BeanMapping(ignoreByDefault = true)
    public abstract Contribution fromDTO(ContributionDTO dto, @MappingTarget Contribution contribution);
}
