package net.blueshell.api.user.security

import net.blueshell.api.user.model.Address
import net.blueshell.api.user.service.AddressService
import net.blueshell.api.platform.config.permission.BasePermissionEvaluator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class AddressPermission @Autowired constructor(service: AddressService) :
    BasePermissionEvaluator<Address, Long, AddressService>(service) {
    override fun hasPermission(authentication: Authentication?, `object`: Any?, permission: String?): Boolean {
        if (authentication == null || `object` == null || permission == null) {
            return false
        }
        val target = `object` as Address
        return when (permission) {
            "read", "write" -> (principal?.addressId == target.id)
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }

        val target = service.findById(targetId as Long)
        return target != null && hasPermission(authentication, target, permission)
    }
}
