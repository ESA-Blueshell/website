package net.blueshell.api.controller.contribution;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import net.blueshell.api.base.BaseController;
import net.blueshell.api.dto.contribution.ContributionReminderDTO;
import net.blueshell.api.mapper.contribution.ContributionReminderMapper;
import net.blueshell.api.service.contribution.ContributionReminderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "ContributionReminders")
public class ContributionReminderController extends BaseController<ContributionReminderService, ContributionReminderMapper> {

    @Autowired
    public ContributionReminderController(ContributionReminderService service, ContributionReminderMapper mapper) {
        super(service, mapper);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders")
    @ResponseStatus(HttpStatus.CREATED)
    public ContributionReminderDTO sendContributionReminder(@Valid @RequestBody ContributionReminderDTO dto) {
        var reminder = mapper.fromDTO(dto);
        service.sendReminder(reminder);
        reminder = service.create(reminder);
        return mapper.toDTO(reminder);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @PostMapping("/contributionReminders/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ContributionReminderDTO> sendContributionReminderBatch(@Valid @RequestBody List<ContributionReminderDTO> dtos) {
        var reminders = mapper.fromDTOs(dtos);
        service.sendReminders(reminders);
        reminders = service.createAll(reminders);
        return mapper.toDTOs(reminders);
    }

    @PreAuthorize("hasAuthority('BOARD')")
    @GetMapping("/contributionReminders")
    public List<ContributionReminderDTO> findContributionReminders(@RequestParam(required = false) Long contributionPeriodId) {
        var contributions = service.findByContributionPeriodId(contributionPeriodId);
        return mapper.toDTOs(contributions);
    }
}
