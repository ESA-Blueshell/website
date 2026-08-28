package net.blueshell.api.board.domain

import net.blueshell.api.security.permission.BasePermissionEvaluator

import net.blueshell.api.board.persistence.Board
import net.blueshell.api.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class BoardPermission @Autowired constructor(service: BoardService) :
    BasePermissionEvaluator<Board, Long, BoardService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            "read" -> true
            "write", "delete", "members" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (id == null) return hasPermission(authentication, null, permission)
        val board = service.findById(id as Long)
        return hasPermission(authentication, board, permission)
    }
}
