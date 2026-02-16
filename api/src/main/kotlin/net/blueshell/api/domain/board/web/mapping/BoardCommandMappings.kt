package net.blueshell.api.domain.board.web.mapping

import net.blueshell.api.domain.board.command.AddBoardMemberCommand
import net.blueshell.api.domain.board.command.CreateBoardCommand
import net.blueshell.api.domain.board.command.UpdateBoardCommand
import net.blueshell.api.domain.board.web.dto.request.AddBoardMemberRequest
import net.blueshell.api.domain.board.web.dto.request.CreateBoardRequest
import net.blueshell.api.domain.board.web.dto.request.UpdateBoardRequest
import tech.mappie.api.ObjectMappie

object CreateBoardRequestToCommandMapper : ObjectMappie<CreateBoardRequest, CreateBoardCommand>() {
    override fun map(from: CreateBoardRequest) = mapping {
        CreateBoardCommand::name fromValue from.name!!
        CreateBoardCommand::candidate fromValue from.candidate!!
        CreateBoardCommand::startDate fromValue from.startDate!!
        CreateBoardCommand::endDate fromProperty from::endDate
        CreateBoardCommand::pictureId fromProperty from::pictureId
    }
}

fun CreateBoardRequest.asCommand(): CreateBoardCommand =
    CreateBoardRequestToCommandMapper.map(this)

class UpdateBoardRequestToCommandMapper(private val id: Long) : ObjectMappie<UpdateBoardRequest, UpdateBoardCommand>() {
    override fun map(from: UpdateBoardRequest) = mapping {
        UpdateBoardCommand::id fromValue id
        UpdateBoardCommand::name fromValue from.name!!
        UpdateBoardCommand::candidate fromValue from.candidate!!
        UpdateBoardCommand::startDate fromValue from.startDate!!
        UpdateBoardCommand::endDate fromProperty from::endDate
        UpdateBoardCommand::pictureId fromProperty from::pictureId
        UpdateBoardCommand::version fromValue  from.version!!
    }
}

fun UpdateBoardRequest.asCommand(id: Long): UpdateBoardCommand =
    UpdateBoardRequestToCommandMapper(id).map(this)

class AddBoardMemberRequestToCommandMapper(private val boardId: Long) : ObjectMappie<AddBoardMemberRequest, AddBoardMemberCommand>() {
    override fun map(from: AddBoardMemberRequest) = mapping {
        AddBoardMemberCommand::boardId fromValue boardId
        AddBoardMemberCommand::userId fromValue from.userId!!
        AddBoardMemberCommand::role fromValue from.role!!
        AddBoardMemberCommand::startDate fromValue from.startDate!!
        AddBoardMemberCommand::endDate fromProperty from::endDate
    }
}

fun AddBoardMemberRequest.asCommand(boardId: Long): AddBoardMemberCommand =
    AddBoardMemberRequestToCommandMapper(boardId).map(this)
