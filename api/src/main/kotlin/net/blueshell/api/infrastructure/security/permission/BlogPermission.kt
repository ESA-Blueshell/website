package net.blueshell.api.infrastructure.security.permission

import net.blueshell.api.domain.blog.application.BlogService
import net.blueshell.api.domain.blog.persistence.Blog
import net.blueshell.api.infrastructure.security.SecurityUtils
import net.blueshell.api.shared.enums.Role
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.Authentication
import org.springframework.stereotype.Component

@Component
class BlogPermission @Autowired constructor(service: BlogService) :
    BasePermissionEvaluator<Blog, Long, BlogService>(service) {
    override fun hasPermission(authentication: Authentication?, entity: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        val isBoard = SecurityUtils.hasAuthority(authentication, Role.BOARD)
        return when (permission) {
            "read" -> true
            "write", "delete" -> isBoard
            else -> false
        }
    }

    override fun hasPermissionId(authentication: Authentication?, id: Any?, permission: String?): Boolean {
        if (authentication == null || permission == null) {
            return false
        }
        if (id == null) return hasPermission(authentication, null, permission)
        val blog = service.findById(id as Long)
        return hasPermission(authentication, blog, permission)
    }
}
