package net.blueshell.api.domain.contribution.web.mapping.request

import net.blueshell.api.domain.contribution.command.ContributionReminderItem
import net.blueshell.api.domain.contribution.command.CreateContributionCommand
import net.blueshell.api.domain.contribution.command.CreateContributionPeriodCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderBatchCommand
import net.blueshell.api.domain.contribution.command.SendContributionReminderCommand
import net.blueshell.api.domain.contribution.command.UpdateContributionPeriodCommand
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionReminderRequest
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionRequest
import net.blueshell.api.domain.contribution.web.dto.request.UpdateContributionPeriodRequest

fun CreateContributionRequest.asCommand(): CreateContributionCommand =
    CreateContributionCommand(
        userId = this.userId!!,
        contributionPeriodId = this.contributionPeriodId!!,
    )

fun CreateContributionPeriodRequest.asCommand(): CreateContributionPeriodCommand =
    CreateContributionPeriodCommand(
        startDate = this.startDate!!,
        endDate = this.endDate!!,
        halfYearFee = this.halfYearFee!!,
        fullYearFee = this.fullYearFee!!,
        alumniFee = this.alumniFee!!,
        listId = this.listId,
    )

fun UpdateContributionPeriodRequest.asCommand(id: Long): UpdateContributionPeriodCommand =
    UpdateContributionPeriodCommand(
        id = id,
        startDate = this.startDate!!,
        endDate = this.endDate!!,
        halfYearFee = this.halfYearFee!!,
        fullYearFee = this.fullYearFee!!,
        alumniFee = this.alumniFee!!,
        listId = this.listId,
        version = this.version!!,
    )

fun CreateContributionReminderRequest.asCommand(): SendContributionReminderCommand =
    SendContributionReminderCommand(
        userId = this.userId!!,
        contributionPeriodId = this.contributionPeriodId!!,
    )

fun MutableList<CreateContributionReminderRequest>.asCommand(): SendContributionReminderBatchCommand =
    SendContributionReminderBatchCommand(
        items = this.map {
            ContributionReminderItem(
                userId = it.userId!!,
                contributionPeriodId = it.contributionPeriodId!!,
            )
        }.toMutableList(),
    )
