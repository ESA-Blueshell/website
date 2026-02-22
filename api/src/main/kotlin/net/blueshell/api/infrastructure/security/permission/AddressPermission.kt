package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.infrastructure.security.permission.BasePermissionEvaluator
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class AddressPermission @Autowired constructor(service: AddressService) :
    BasePermissionEvaluator<Address, Long, AddressService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        if (entity == null) {
            return when (permission) {
                "read", "write", "delete" -> isBoard
                else -> false
            }
        }

        val target = entity as Address
        val principal = SecurityUtils.principalFrom(authentication)
        return when (permission) {
            "read", "write" -> isBoard || (principal?.addressId == target.id)
            "delete" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (id == null) return hasPermission(authentication, null, permission)

        val target = service.findById(id as Long)
        return hasPermission(authentication, target, permission)
    }
}
