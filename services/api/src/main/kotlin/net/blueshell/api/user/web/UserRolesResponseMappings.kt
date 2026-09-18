package net.blueshell.api.user.web

import net.blueshell.api.shared.enums.Role
import net.blueshell.api.user.domain.RoleStanding
import net.blueshell.api.user.persistence.RoleChange

fun RoleStanding.asResponse(): UserRolesResponse =
    UserRolesResponse(
        userId = userId,
        roles = roles.byRank(),
        granted = granted.byRank(),
        derived = derived.entries.sortedBy { it.key.ordinal }.map { DerivedRoleResponse(it.key, it.value) },
        implied = implied.byRank(),
        assignable = assignable.byRank(),
    )

fun RoleChange.asResponse(): RoleChangeResponse =
    RoleChangeResponse(
        id = requireNotNull(id),
        actorId = requireNotNull(actor.id),
        actorName = actor.fullName,
        changedAt = changedAt,
        before = rolesBefore.byRank(),
        after = rolesAfter.byRank(),
        note = note,
    )

// The enum is declared in ascending privilege, so its own order is the one a reader expects.
private fun Collection<Role>.byRank(): List<Role> = sortedBy { it.ordinal }
