package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.auth.security.IdentityProvider
import net.blueshell.api.contribution.web.dto.ContributionReminderDTO
import net.blueshell.api.contribution.persistence.asDto
import net.blueshell.api.contribution.application.ContributionReminderService
import net.blueshell.api.contribution.web.dto.asEntity
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    val reminderService: ContributionReminderService
) : IdentityProvider() {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody dto: ContributionReminderDTO): ContributionReminderDTO {
        var reminder = dto.asEntity()
        reminder = reminderService.create(reminder)
        reminderService.sendReminder(reminder)
        return reminder.asDto()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody dtos: MutableList<ContributionReminderDTO>): MutableList<ContributionReminderDTO> {
        var reminders = dtos.map { it.asEntity() }.toMutableList()
        reminders = reminderService.createAll(reminders)
        reminderService.sendReminders(reminders)
        return reminders.map { it.asDto() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderDTO> {
        val contributions = reminderService.findByContributionPeriodId(contributionPeriodId)
        return contributions.map { it.asDto() }.toMutableList()
    }
}
