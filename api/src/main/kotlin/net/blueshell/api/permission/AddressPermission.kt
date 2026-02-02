package net.blueshell.api.permission

import lombok.extern.slf4j.Slf4j
import net.blueshell.api.base.BasePermissionEvaluator
import net.blueshell.api.model.Address
import net.blueshell.api.service.AddressService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Slf4j
@Component
class AddressPermission @Autowired constructor(service: AddressService?) :
    BasePermissionEvaluator<Address?, AddressService?>(service) {
    override fun hasPermission(authentication: Authentication?, `object`: Any?, permission: String?): Boolean {
        if (authentication == null || `object` == null || permission == null) {
            return false
        }
        val target = `object` as Address
        val principal = getPrincipal()
        return when (permission) {
            "read", "write" -> (principal.getAddressId() == target.getId())
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, targetId: Any?, permission: String?): Boolean {
        if (authentication == null || targetId == null || permission == null) {
            return false
        }

        val target = service!!.findById(targetId as Long)
        return target != null && hasPermission(authentication, target, permission)
    }
}
