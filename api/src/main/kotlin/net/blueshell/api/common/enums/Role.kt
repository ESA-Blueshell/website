package net.blueshell.api.common.enums

import io.swagger.v3.oas.annotations.media.Schema
import lombok.Getter
import java.util.*
import java.util.List

@Schema(enumAsRef = true)
enum class Role(@field:Getter private val reprString: String?, vararg inheritedRoles: Role?) {
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

    private val inheritedRoles: Array<Role?>

    init {
        this.inheritedRoles = inheritedRoles
    }

    fun matchesRole(role: Role?): Boolean {
        return role == this || Arrays.stream<Role?>(inheritedRoles).anyMatch { r: Role? -> r!!.matchesRole(role) }
    }

    val allInheritedRoles: MutableSet<Role?>
        /**
         * Search for all inherited roles of this Role.
         */
        get() {
            val res: MutableSet<Role?> =
                HashSet<Role?>()
            res.add(this)
            val unexplored =
                ArrayDeque<Role>(
                    List.of<Role?>(*inheritedRoles)
                )
            while (!unexplored.isEmpty()) {
                val currentRole = unexplored.remove()
                res.add(currentRole)
                unexplored.addAll(
                    Arrays.stream<Role?>(currentRole.inheritedRoles)
                        .filter { role: Role? -> !res.contains(role) }.toList()
                )
            }
            return res
        }

    val authorities: MutableCollection<Any?>
        get() = mutableSetOf<Any?>(this.allInheritedRoles)

    val name: Any?
        get() = this.reprString

    companion object {
        fun allThatInherit(base: Role?): MutableSet<Role?> {
            val res = EnumSet.noneOf<Role?>(Role::class.java)
            for (r in entries) {
                if (r.matchesRole(base)) {
                    res.add(r)
                }
            }
            return res
        }
    }
}
