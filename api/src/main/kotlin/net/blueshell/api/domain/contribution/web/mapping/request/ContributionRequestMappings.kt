package net.blueshell.api.domain.contribution.web.mapping.request

import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionReminderRequest
import net.blueshell.api.domain.contribution.web.dto.request.CreateContributionRequest
import net.blueshell.api.domain.contribution.web.dto.request.UpdateContributionPeriodRequest
import tech.mappie.api.ObjectMappie

object CreateContributionRequestToCommandMapper : ObjectMappie<CreateContributionRequest, CreateContributionCommand>() {
    override fun map(from: CreateContributionRequest) = mapping {
        CreateContributionCommand::userId fromValue from.userId!!
        CreateContributionCommand::contributionPeriodId fromValue from.contributionPeriodId!!
    }
}

internal data class UpdateContributionPeriodCommandRequest(
    val id: Long,
    val request: UpdateContributionPeriodRequest
)

object CreateContributionPeriodRequestToCommandMapper : ObjectMappie<CreateContributionPeriodRequest, CreateContributionPeriodCommand>() {
    override fun map(from: CreateContributionPeriodRequest) = mapping {
        CreateContributionPeriodCommand::startDate fromValue from.startDate!!
        CreateContributionPeriodCommand::endDate fromValue from.endDate!!
        CreateContributionPeriodCommand::halfYearFee fromValue from.halfYearFee!!
        CreateContributionPeriodCommand::fullYearFee fromValue from.fullYearFee!!
        CreateContributionPeriodCommand::alumniFee fromValue from.alumniFee!!
        CreateContributionPeriodCommand::listId fromValue from.listId
    }
}

internal object UpdateContributionPeriodCommandRequestToCommandMapper : ObjectMappie<UpdateContributionPeriodCommandRequest, UpdateContributionPeriodCommand>() {
    override fun map(from: UpdateContributionPeriodCommandRequest) = mapping {
        UpdateContributionPeriodCommand::id fromProperty from::id
        UpdateContributionPeriodCommand::startDate fromValue from.request.startDate!!
        UpdateContributionPeriodCommand::endDate fromValue from.request.endDate!!
        UpdateContributionPeriodCommand::halfYearFee fromValue from.request.halfYearFee!!
        UpdateContributionPeriodCommand::fullYearFee fromValue from.request.fullYearFee!!
        UpdateContributionPeriodCommand::alumniFee fromValue from.request.alumniFee!!
        UpdateContributionPeriodCommand::listId fromValue from.request.listId
        UpdateContributionPeriodCommand::version fromValue from.request.version!!
    }
}

object CreateContributionReminderRequestToCommandMapper : ObjectMappie<CreateContributionReminderRequest, SendContributionReminderCommand>() {
    override fun map(from: CreateContributionReminderRequest) = mapping {
        SendContributionReminderCommand::userId fromValue from.userId!!
        SendContributionReminderCommand::contributionPeriodId fromValue from.contributionPeriodId!!
    }
}

internal data class ContributionReminderBatchCommandRequest(
    val requests: MutableList<CreateContributionReminderRequest>
)

internal object ContributionReminderBatchCommandRequestToCommandMapper : ObjectMappie<ContributionReminderBatchCommandRequest, SendContributionReminderBatchCommand>() {
    override fun map(from: ContributionReminderBatchCommandRequest) = mapping {
        SendContributionReminderBatchCommand::items fromValue from.requests.map {
            ContributionReminderItem(
                userId = it.userId!!,
                contributionPeriodId = it.contributionPeriodId!!
            )
        }.toMutableList()
    }
}

fun CreateContributionRequest.asCommand(): CreateContributionCommand = CreateContributionRequestToCommandMapper.map(this)

fun CreateContributionPeriodRequest.asCommand(): CreateContributionPeriodCommand =
    CreateContributionPeriodRequestToCommandMapper.map(this)

fun UpdateContributionPeriodRequest.asCommand(id: Long): UpdateContributionPeriodCommand =
    UpdateContributionPeriodCommandRequestToCommandMapper.map(UpdateContributionPeriodCommandRequest(id, this))

fun CreateContributionReminderRequest.asCommand(): SendContributionReminderCommand =
    CreateContributionReminderRequestToCommandMapper.map(this)

fun MutableList<CreateContributionReminderRequest>.asCommand(): SendContributionReminderBatchCommand =
    ContributionReminderBatchCommandRequestToCommandMapper.map(ContributionReminderBatchCommandRequest(this))
