package net.blueshell.api.controller.contribution

import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import net.blueshell.api.base.BaseController
import net.blueshell.api.dto.contribution.ContributionReminderDTO
import net.blueshell.api.mapper.contribution.ContributionReminderMapper
import net.blueshell.api.service.contribution.ContributionReminderService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@Tag(name = "ContributionReminders")
class ContributionReminderController @Autowired constructor(
    service: ContributionReminderService?,
    mapper: ContributionReminderMapper?
) : BaseController<ContributionReminderService?, ContributionReminderMapper?>(service, mapper) {
    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminder(@Valid @RequestBody dto: @Valid ContributionReminderDTO?): ContributionReminderDTO? {
        var reminder = mapper!!.fromDTO(dto)
        service!!.sendReminder(reminder)
        reminder = service.create(reminder)
        return mapper.toDTO(reminder)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendContributionReminderBatch(@Valid @RequestBody dtos: @Valid MutableList<ContributionReminderDTO?>): MutableList<ContributionReminderDTO?>? {
        var reminders = mapper!!.fromDTOs(dtos)
        service!!.sendReminders(reminders)
        reminders = service.createAll(reminders)
        return mapper.toDTOs(reminders)
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    fun findContributionReminders(@RequestParam(required = false) contributionPeriodId: Long?): MutableList<ContributionReminderDTO?>? {
        val contributions = service!!.findByContributionPeriodId(contributionPeriodId)
        return mapper!!.toDTOs(contributions)
    }
}
