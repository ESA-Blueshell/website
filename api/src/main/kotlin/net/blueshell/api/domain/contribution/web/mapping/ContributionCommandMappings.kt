package net.blueshell.api.domain.contribution.web.mapping

import net.blueshell.api.domain.contribution.command.*
import net.blueshell.api.domain.contribution.web.dto.CreateContributionPeriodRequest
import net.blueshell.api.domain.contribution.web.dto.CreateContributionReminderRequest
import net.blueshell.api.domain.contribution.web.dto.CreateContributionRequest
import net.blueshell.api.domain.contribution.web.dto.UpdateContributionPeriodRequest
import tech.mappie.api.ObjectMappie

object CreateContributionRequestToCommandMapper : ObjectMappie<CreateContributionRequest, CreateContributionCommand>() {
    override fun map(from: CreateContributionRequest) = mapping {
        CreateContributionCommand::userId fromProperty { from.userId!! }
        CreateContributionCommand::contributionPeriodId fromProperty { from.contributionPeriodId!! }
    }
}

private data class UpdateContributionPeriodCommandRequest(
    val id: Long,
    val request: UpdateContributionPeriodRequest
)

object CreateContributionPeriodRequestToCommandMapper : ObjectMappie<CreateContributionPeriodRequest, CreateContributionPeriodCommand>() {
    override fun map(from: CreateContributionPeriodRequest) = mapping {
        CreateContributionPeriodCommand::startDate fromProperty { from.startDate!! }
        CreateContributionPeriodCommand::endDate fromProperty { from.endDate!! }
        CreateContributionPeriodCommand::halfYearFee fromProperty { from.halfYearFee!! }
        CreateContributionPeriodCommand::fullYearFee fromProperty { from.fullYearFee!! }
        CreateContributionPeriodCommand::alumniFee fromProperty { from.alumniFee!! }
        CreateContributionPeriodCommand::listId fromProperty { from.listId }
    }
}

object UpdateContributionPeriodCommandRequestToCommandMapper : ObjectMappie<UpdateContributionPeriodCommandRequest, UpdateContributionPeriodCommand>() {
    override fun map(from: UpdateContributionPeriodCommandRequest) = mapping {
        UpdateContributionPeriodCommand::id fromProperty from::id
        UpdateContributionPeriodCommand::startDate fromProperty { from.request.startDate!! }
        UpdateContributionPeriodCommand::endDate fromProperty { from.request.endDate!! }
        UpdateContributionPeriodCommand::halfYearFee fromProperty { from.request.halfYearFee!! }
        UpdateContributionPeriodCommand::fullYearFee fromProperty { from.request.fullYearFee!! }
        UpdateContributionPeriodCommand::alumniFee fromProperty { from.request.alumniFee!! }
        UpdateContributionPeriodCommand::listId fromProperty { from.request.listId }
        UpdateContributionPeriodCommand::version fromProperty { from.request.version }
    }
}

object CreateContributionReminderRequestToCommandMapper : ObjectMappie<CreateContributionReminderRequest, SendContributionReminderCommand>() {
    override fun map(from: CreateContributionReminderRequest) = mapping {
        SendContributionReminderCommand::userId fromProperty { from.userId!! }
        SendContributionReminderCommand::contributionPeriodId fromProperty { from.contributionPeriodId!! }
    }
}

private data class ContributionReminderBatchCommandRequest(
    val requests: MutableList<CreateContributionReminderRequest>
)

object ContributionReminderBatchCommandRequestToCommandMapper : ObjectMappie<ContributionReminderBatchCommandRequest, SendContributionReminderBatchCommand>() {
    override fun map(from: ContributionReminderBatchCommandRequest) = mapping {
        SendContributionReminderBatchCommand::items fromProperty {
            from.requests.map {
                ContributionReminderItem(
                    userId = it.userId!!,
                    contributionPeriodId = it.contributionPeriodId!!
                )
            }.toMutableList()
        }
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
