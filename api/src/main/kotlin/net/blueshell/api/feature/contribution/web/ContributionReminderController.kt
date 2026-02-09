package net.blueshell.api.feature.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.shared.web.BaseController
import net.blueshell.api.feature.contribution.dto.ContributionReminderDTO
import net.blueshell.api.feature.contribution.mapper.ContributionReminderMapper
import net.blueshell.api.feature.contribution.service.ContributionReminderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    service: ContributionReminderService,
    mapper: ContributionReminderMapper
) : BaseController<ContributionReminderService, ContributionReminderMapper>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody dto: ContributionReminderDTO): ContributionReminderDTO {
        var reminder = mapper.fromDTO(dto)
        service.sendReminder(reminder)
        reminder = service.create(reminder)
        return mapper.toDTO(reminder)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody dtos: MutableList<ContributionReminderDTO>): MutableList<ContributionReminderDTO> {
        var reminders = mapper.fromDTOs(dtos)
        service.sendReminders(reminders)
        reminders = service.createAll(reminders)
        return mapper.toDTOs(reminders)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderDTO> {
        val contributions = service.findByContributionPeriodId(contributionPeriodId)
        return mapper.toDTOs(contributions)
    }
}
