package net.blueshell.api.domain.user

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Members and their accounts: the `User` aggregate, postal addresses, member profiles and the
 * dated `Membership` rows that answer whether someone counted as a member on a given day.
 *
 * Erasure is a soft delete, so nothing cascades at the database level — other modules react to
 * the lifecycle events published here, and signup is finished through the `SignupCompletion`
 * port this module declares and `auth` implements.
 */
@PackageInfo
@ApplicationModule(id = "user")
class ModuleMetadata
