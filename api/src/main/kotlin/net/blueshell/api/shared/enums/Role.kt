package net.blueshell.api.shared.enums

import io.swagger.v3.oas.annotations.media.Schema
import java.util.*

@Schema(enumAsRef = true)
enum class Role(val reprString: String, vararg inheritedRoles: Role) {
    ANONYMOUS("ANONYMOUS"),
    VEGAN("VEGAN"),
    GUEST("GUEST", ANONYMOUS),
    COMPANY("COMPANY"),
    MEMBER("MEMBER", GUEST),
    COMMITTEE("COMMITTEE", MEMBER),
    BOARD("BOARD", COMMITTEE),
    TREASURER("TREASURER", BOARD),
    ADMIN("ADMIN", TREASURER),
    SYSTEM("SYSTEM", ADMIN),
    ;

    private val inheritedRoles: Array<Role> = arrayOf(*inheritedRoles)

    fun matchesRole(role: Role): Boolean {
        return role == this || inheritedRoles.any { it.matchesRole(role) }
    }

    val allInheritedRoles: MutableSet<Role>
        /**
         * Search for all inherited roles of this Role.
         */
        get() {
            val res: MutableSet<Role> = HashSet<Role>()
            res.add(this)
            val unexplored = ArrayDeque<Role>(inheritedRoles.toList())
            while (!unexplored.isEmpty()) {
                val currentRole = unexplored.remove()
                res.add(currentRole)
                for (role in currentRole.inheritedRoles) {
                    if (!res.contains(role)) {
                        unexplored.add(role)
                    }
                }
            }
            return res
        }

    val authorities: MutableCollection<Any>
        get() = this.allInheritedRoles.toMutableSet()

    companion object {
        fun allThatInherit(base: Role): MutableSet<Role> {
            val res = EnumSet.noneOf(Role::class.java)
            for (r in entries) {
                if (r.matchesRole(base)) {
                    res.add(r)
                }
            }
            return res
        }
    }
}
