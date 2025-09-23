package net.blueshell.api.mapper;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.ContributionDTO;
import net.blueshell.api.dto.ContributionPeriodDTO;
import net.blueshell.api.dto.user.AdvancedUserDTO;
import net.blueshell.api.model.Contribution;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.model.User;
import net.blueshell.api.service.ContributionPeriodService;
import net.blueshell.api.service.ContributionService;
import org.mapstruct.*;
import org.springframework.beans.factory.annotation.Autowired;

@Mapper(componentModel = "spring")
public abstract class ContributionPeriodMapper extends BaseMapper<ContributionPeriod, ContributionPeriodDTO> {

    @Autowired
    private ContributionPeriodService contributionPeriods;

    @ObjectFactory
    protected ContributionPeriod contributionPeriod(@TargetType Class<ContributionPeriod> type, ContributionPeriodDTO dto) {
        if (dto.getId() != null) {
            return contributionPeriods.findById(dto.getId());
        }
        return new ContributionPeriod();
    }

    @Mapping(target = "id")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "halfYearFee")
    @Mapping(target = "fullYearFee")
    @Mapping(target = "alumniFee")
    @Mapping(target = "listId")
    public abstract ContributionPeriodDTO toDTO(ContributionPeriod contributionPeriod);


    @InheritInverseConfiguration
    @Mapping(target = "deletedAt", ignore = true)
    @Mapping(target = "listId", ignore = true)
    @Mapping(target = "contributions", ignore = true)
    public abstract ContributionPeriod fromDTO(ContributionPeriodDTO dto);
}
