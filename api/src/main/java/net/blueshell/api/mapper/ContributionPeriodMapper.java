package net.blueshell.api.mapper;


import net.blueshell.api.dto.ContributionPeriodDTO;
import net.blueshell.api.model.ContributionPeriod;
import net.blueshell.api.base.BaseMapper;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public abstract class ContributionPeriodMapper extends BaseMapper<ContributionPeriod, ContributionPeriodDTO> {

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
    @Mapping(target = "contributions", ignore = true)
    public abstract ContributionPeriod fromDTO(ContributionPeriodDTO dto);

}
