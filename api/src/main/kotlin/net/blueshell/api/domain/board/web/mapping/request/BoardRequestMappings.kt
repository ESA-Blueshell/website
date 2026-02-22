package net.blueshell.api.domain.board.web.mapping.request

import net.blueshell.api.domain.board.command.AddBoardMemberCommand
import net.blueshell.api.domain.board.command.CreateBoardCommand
import net.blueshell.api.domain.board.command.UpdateBoardCommand
import net.blueshell.api.domain.board.web.dto.request.AddBoardMemberRequest
import net.blueshell.api.domain.board.web.dto.request.CreateBoardRequest
import net.blueshell.api.domain.board.web.dto.request.UpdateBoardRequest

fun CreateBoardRequest.asCommand(): CreateBoardCommand =
    CreateBoardCommand(
        name = this.name!!,
        candidate = this.candidate!!,
        startDate = this.startDate!!,
        endDate = this.endDate,
        pictureId = this.pictureId,
    )

fun UpdateBoardRequest.asCommand(id: Long): UpdateBoardCommand =
    UpdateBoardCommand(
        id = id,
        name = this.name!!,
        candidate = this.candidate!!,
        startDate = this.startDate!!,
        endDate = this.endDate,
        pictureId = this.pictureId,
        version = this.version!!,
    )

fun AddBoardMemberRequest.asCommand(boardId: Long): AddBoardMemberCommand =
    AddBoardMemberCommand(
        boardId = boardId,
        userId = this.userId!!,
        role = this.role!!,
        startDate = this.startDate!!,
        endDate = this.endDate,
    )
