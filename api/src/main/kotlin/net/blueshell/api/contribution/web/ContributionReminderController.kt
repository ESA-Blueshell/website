package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.auth.security.IdentityProvider
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.contribution.web.mapper.ContributionReminderMapper
import net.blueshell.api.contribution.application.ContributionReminderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    val reminderService: ContributionReminderService,
    val reminderMapper: ContributionReminderMapper
) : IdentityProvider() {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody dto: ContributionReminderDTO): ContributionReminderDTO {
        var reminder = reminderMapper.fromDTO(dto)
        reminder = reminderService.create(reminder)
        reminderService.sendReminder(reminder)
        return reminderMapper.toDTO(reminder)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody dtos: MutableList<ContributionReminderDTO>): MutableList<ContributionReminderDTO> {
        var reminders = reminderMapper.fromDTOs(dtos)
        reminders = reminderService.createAll(reminders)
        reminderService.sendReminders(reminders)
        return reminderMapper.toDTOs(reminders)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderDTO> {
        val contributions = reminderService.findByContributionPeriodId(contributionPeriodId)
        return reminderMapper.toDTOs(contributions)
    }
}
