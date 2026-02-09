package net.blueshell.api.contribution.api.mapper

import net.blueshell.api.contribution.api.dto.ContributionDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.domain.model.Contribution
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

    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(contribution: Contribution): ContributionDTO
}
