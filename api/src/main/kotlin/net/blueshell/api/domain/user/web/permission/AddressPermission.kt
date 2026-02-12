package net.blueshell.api.domain.user.web.permission

import net.blueshell.api.domain.user.application.AddressService
import net.blueshell.api.domain.user.persistence.Address
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class AddressPermission @Autowired constructor(service: AddressService) :
    BasePermissionEvaluator<Address, Long, AddressService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || entity == null || permission == null) {
            return false
        }
        val target = entity as Address
        return when (permission) {
            "read", "write" -> (principal?.addressId == target.id)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || id == null || permission == null) {
            return false
        }

        val target = service.findById(id as Long)
        return hasPermission(authentication, target, permission)
    }
}
