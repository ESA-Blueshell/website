package net.blueshell.api.board.web

import net.blueshell.api.board.domain.BoardInput
import net.blueshell.api.board.domain.BoardMemberInput

fun BoardRequest.asInput() = BoardInput(number, name, candidate, startDate, endDate, photo, cheer, accent, description)

fun AddBoardMemberRequest.asInput() = BoardMemberInput(role, startDate, endDate, displayName, nickname, description, portrait)

fun UpdateBoardMemberRequest.asInput() = BoardMemberInput(role, startDate, endDate, displayName, nickname, description, portrait)
