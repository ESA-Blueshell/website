package net.blueshell.api.mapper.contribution

import net.blueshell.api.base.BaseMapper
import net.blueshell.api.dto.contribution.ContributionReminderDTO
import net.blueshell.api.model.contribution.ContributionReminder
import org.mapstruct.BeanMapping
import org.mapstruct.Mapper
import org.mapstruct.Mapping
import org.mapstruct.MappingTarget

@Mapper(componentModel = "spring")
abstract class ContributionReminderMapper : BaseMapper<ContributionReminder?, ContributionReminderDTO?>() {
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract override fun toDTO(reminder: ContributionReminder?): ContributionReminderDTO?

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    abstract fun fromDTO(
        dto: ContributionReminderDTO?,
        @MappingTarget reminder: ContributionReminder?
    ): ContributionReminder?
}
