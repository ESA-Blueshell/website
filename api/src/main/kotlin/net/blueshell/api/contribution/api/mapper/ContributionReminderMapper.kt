package net.blueshell.api.contribution.api.mapper

import net.blueshell.api.contribution.api.dto.ContributionReminderDTO
import net.blueshell.api.shared.mapper.BaseMapper
import net.blueshell.api.contribution.domain.model.ContributionReminder
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class ContributionReminderMapper : BaseMapper<ContributionReminder, ContributionReminderDTO>() {
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(
        dto: ContributionReminderDTO,
        @MappingTarget reminder: ContributionReminder
    ): ContributionReminder

    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(reminder: ContributionReminder): ContributionReminderDTO
}
