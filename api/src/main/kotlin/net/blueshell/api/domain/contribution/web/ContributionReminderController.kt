package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.auth.security.IdentityProvider
import net.blueshell.api.domain.contribution.application.ContributionReminderService
import net.blueshell.api.domain.contribution.web.dto.ContributionReminderResponse
import net.blueshell.api.domain.contribution.web.dto.CreateContributionReminderRequest
import net.blueshell.api.domain.contribution.web.mapping.asEntity
import net.blueshell.api.domain.contribution.web.mapping.asResponse
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
    fun sendContributionReminder(@Valid @RequestBody request: CreateContributionReminderRequest): ContributionReminderResponse {
        var reminder = request.asEntity()
        reminder = reminderService.create(reminder)
        reminderService.sendReminder(reminder)
        return reminder.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody requests: MutableList<CreateContributionReminderRequest>): MutableList<ContributionReminderResponse> {
        var reminders = requests.map { it.asEntity() }.toMutableList()
        reminders = reminderService.createAll(reminders)
        reminderService.sendReminders(reminders)
        return reminders.map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderResponse> {
        val contributions = reminderService.findByContributionPeriodId(contributionPeriodId)
        return contributions.map { it.asResponse() }.toMutableList()
    }
}
