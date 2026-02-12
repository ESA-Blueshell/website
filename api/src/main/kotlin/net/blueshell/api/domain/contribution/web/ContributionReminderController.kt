package net.blueshell.api.domain.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.web.dto.ContributionReminderResponse
import net.blueshell.api.domain.contribution.web.dto.CreateContributionReminderRequest
import net.blueshell.api.domain.contribution.web.mapping.asCommand
import net.blueshell.api.domain.contribution.web.mapping.asResponse
import net.blueshell.api.shared.command.CommandBus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    private val commandBus: CommandBus
) {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody request: CreateContributionReminderRequest): ContributionReminderResponse {
        val reminder = commandBus.dispatch(request.asCommand())
        return reminder.asResponse()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody requests: MutableList<CreateContributionReminderRequest>): MutableList<ContributionReminderResponse> {
        val reminders = commandBus.dispatch(requests.asCommand())
        return reminders.map { it.asResponse() }.toMutableList()
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderResponse> {
        val reminders = commandBus.dispatch(FindContributionRemindersCommand(contributionPeriodId))
        return reminders.map { it.asResponse() }.toMutableList()
    }
}
