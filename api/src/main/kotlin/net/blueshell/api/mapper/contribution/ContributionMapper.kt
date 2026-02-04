package net.blueshell.api.mapper.contribution

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.contribution.ContributionDTO
import net.blueshell.api.model.contribution.Contribution
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class ContributionMapper : BaseMapper<Contribution, ContributionDTO>() {
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(dto: ContributionDTO, @MappingTarget contribution: Contribution): Contribution

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(contribution: Contribution): ContributionDTO
}
