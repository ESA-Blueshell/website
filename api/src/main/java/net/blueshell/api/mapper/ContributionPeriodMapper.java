package net.blueshell.api.mapper;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.ContributionPeriodDTO;
import net.blueshell.api.model.contribution.ContributionPeriod;
import org.mapstruct.*;

@Mapper(componentModel = "spring", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public abstract class ContributionPeriodMapper extends BaseMapper<ContributionPeriod, ContributionPeriodDTO> {

    @Mapping(target = "id")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "halfYearFee")
    @Mapping(target = "fullYearFee")
    @Mapping(target = "alumniFee")
    @Mapping(target = "listId")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionPeriodDTO toDTO(ContributionPeriod contributionPeriod);


    @Mapping(target = "id")
    @Mapping(target = "startDate")
    @Mapping(target = "endDate")
    @Mapping(target = "halfYearFee")
    @Mapping(target = "fullYearFee")
    @Mapping(target = "alumniFee")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionPeriod fromDTO(ContributionPeriodDTO dto, @MappingTarget ContributionPeriod contributionPeriod);
}
