package net.blueshell.api.domain.board.application

import net.blueshell.api.domain.board.persistence.BoardMember
import net.blueshell.api.domain.board.persistence.repository.BoardMemberRepository
import net.blueshell.api.shared.service.BaseModelService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class BoardMemberService @Autowired constructor(
    repository: BoardMemberRepository
) : BaseModelService<BoardMember, BoardMember.Id, BoardMemberRepository>(repository)
