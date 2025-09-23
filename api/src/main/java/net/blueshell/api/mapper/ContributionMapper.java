package net.blueshell.api.mapper;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.ContributionDTO;
import net.blueshell.api.model.Contribution;
import net.blueshell.api.service.ContributionService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ContributionMapper extends BaseMapper<Contribution, ContributionDTO> {

    @Autowired
    private ContributionService contributions;

    @ObjectFactory
    protected Contribution contribution(@TargetType Class<Contribution> type, ContributionDTO dto) {
        if (dto.getId() != null) {
            return contributions.findById(dto.getId());
        }
        return new Contribution();
    }

    @Mapping(target = "id", source = "contribution.id")
    @Mapping(target = "userId", expression = "java(contribution.getUser() == null ? null : contribution.getUser().getId())")
    @Mapping(target = "contributionPeriodId", expression = "java(contribution.getContributionPeriod() == null ? null : contribution.getContributionPeriod().getId())")
    public abstract ContributionDTO toDTO(Contribution contribution);

    @InheritInverseConfiguration
    @Mapping(target = "user", ignore = true)
    @Mapping(target = "contributionPeriod", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "paid", ignore = true)
    @Mapping(target = "remindedAt", ignore = true)
    @Mapping(target = "membership", ignore = true)
    public abstract Contribution fromDTO(ContributionDTO dto);
}
