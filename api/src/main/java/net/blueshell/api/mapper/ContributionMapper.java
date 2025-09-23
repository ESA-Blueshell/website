package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.ContributionDTO;
import net.blueshell.api.model.Contribution;
import net.blueshell.api.service.ContributionService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ContributionMapper extends BaseMapper<Contribution, ContributionDTO> {
    @Mapping(target = "id", source = "contribution.id")
    @Mapping(target = "userId", expression = "java(contribution.getUser() == null ? null : contribution.getUser().getId())")
    @Mapping(target = "contributionPeriodId", expression = "java(contribution.getContributionPeriod() == null ? null : contribution.getContributionPeriod().getId())")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionDTO toDTO(Contribution contribution);

    @InheritInverseConfiguration
    @BeanMapping(ignoreByDefault = true)
    public abstract Contribution fromDTO(ContributionDTO dto, @MappingTarget Contribution contribution);
}
