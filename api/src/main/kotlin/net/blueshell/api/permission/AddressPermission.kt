package net.blueshell.api.permission

import net.blueshell.api.base.BasePermissionEvaluator
import net.blueshell.api.model.Address
import net.blueshell.api.service.AddressService
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
