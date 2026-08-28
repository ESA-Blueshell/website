package net.blueshell.api.domain.auth

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Getting in and getting an account: login, password recovery tokens, signup sessions and
 * member activation.
 *
 * Implements `user`'s `SignupCompletion` port — this module decides when a signup is complete,
 * `user` owns the account that results from it.
 */
@PackageInfo
@ApplicationModule(
    id = "auth",
    allowedDependencies = [
        // AbstractJsonJobHandler, which this module's job handlers extend.
        "jobs :: api",
        // Account mail — the signup confirmation and the password-reset link — goes
        // out through EmailSenderService.
        "email :: api",
        // Open kernel: the filter chain, the entry point and CurrentUserProvider.
        "security",
        // Open kernel.
        "shared",
        // DEBT, not a surface. RecoveryController answers with telemetry's
        // RedirectResponse instead of its own. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "telemetry :: legacy-web",
        // Accounts, memberships and profiles are read through UserService,
        // MembershipService and MemberProfileService, and signup is finished through
        // the SignupCompletion port user declares and this module implements.
        "user :: api",
        // RecoveryToken.user is an owning @ManyToOne holding the FK into users.
        // Also DEBT: the signup and password services build User, Address,
        // MemberProfile and Membership rows directly rather than asking user to.
        "user :: entities",
        // DEBT, not a surface. SignupController reuses user's CreateUserRequest,
        // UpsertMemberProfileRequest and their mappers. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "user :: legacy-web",
    ],
)
class ModuleMetadata
