package net.blueshell.api.mapper.contribution;


import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.contribution.ContributionPeriodDTO;
import net.blueshell.api.model.contribution.ContributionPeriod;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
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
