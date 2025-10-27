package net.blueshell.api.mapper.contribution;

import net.blueshell.api.base.BaseMapper;
import net.blueshell.api.dto.contribution.ContributionReminderDTO;
import net.blueshell.api.model.contribution.ContributionReminder;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(componentModel = "spring")
public abstract class ContributionReminderMapper extends BaseMapper<ContributionReminder, ContributionReminderDTO> {
    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionReminderDTO toDTO(ContributionReminder reminder);

    @Mapping(target = "id")
    @Mapping(target = "userId")
    @Mapping(target = "contributionPeriodId")
    @Mapping(target = "version")
    @BeanMapping(ignoreByDefault = true)
    public abstract ContributionReminder fromDTO(ContributionReminderDTO dto, @MappingTarget ContributionReminder reminder);
}
