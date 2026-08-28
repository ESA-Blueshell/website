package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.contribution.domain.ContributionReminderService
import net.blueshell.api.contribution.domain.ContributionReminderUseCases
import net.blueshell.api.contribution.domain.toContributionReminderResults
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    private val service: ContributionReminderService,
    private val useCases: ContributionReminderUseCases,
) {
    @PreAuthorize("hasPermission('__NO_TARGET__', 'ContributionReminder', 'write')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody request: CreateContributionReminderRequest): ContributionReminderResponse {
        val reminder = useCases.send(request.userId, request.contributionPeriodId)
        return reminder.asResponse()
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'ContributionReminder', 'write')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody requests: MutableList<CreateContributionReminderRequest>): MutableList<ContributionReminderResponse> {
        val reminders = useCases.sendBatch(requests.map { it.userId to it.contributionPeriodId })
        return reminders.map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasPermission('__NO_TARGET__', 'ContributionReminder', 'read')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderResponse> {
        val reminders = service.findByContributionPeriodId(contributionPeriodId).toContributionReminderResults()
        return reminders.map { it.asResponse() }.toMutableList()
    }
}
