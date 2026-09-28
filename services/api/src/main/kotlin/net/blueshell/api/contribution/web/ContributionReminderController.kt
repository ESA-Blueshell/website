package net.blueshell.api.contribution.web

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.contribution.domain.ContributionReminderService
import net.blueshell.api.contribution.domain.ContributionReminderUseCases
import net.blueshell.api.security.BoardOnly
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    private val service: ContributionReminderService,
    private val useCases: ContributionReminderUseCases,
) {
    @BoardOnly
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody request: CreateContributionReminderRequest): ContributionReminderResponse {
        val reminder = useCases.send(request.userId, request.contributionPeriodId)
        return reminder.asResponse()
    }

    @BoardOnly
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(
        @Valid @RequestBody requests: MutableList<CreateContributionReminderRequest>,
    ): MutableList<ContributionReminderResponse> {
        val reminders = useCases.sendBatch(requests.map { it.userId to it.contributionPeriodId })
        return reminders.map { it.asResponse() }.toMutableList()
    }

    @BoardOnly
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam contributionPeriodId: Long): MutableList<ContributionReminderResponse> {
        val reminders = service.findByContributionPeriodId(contributionPeriodId)
        return reminders.map { it.asResponse() }.toMutableList()
    }
}
