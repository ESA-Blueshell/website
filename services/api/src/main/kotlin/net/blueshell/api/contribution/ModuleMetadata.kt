package net.blueshell.api.contribution

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Membership fees: the yearly `ContributionPeriod`, the per-member `Contribution` rows recording
 * what is owed and what was paid, and the reminders chased against the unpaid ones.
 *
 * Bulk operations over a period live here rather than in a generic bulk facility, because they
 * are one transaction over this module's own rows.
 */
@PackageInfo
@ApplicationModule(
    id = "contribution",
    allowedDependencies = [
        // AbstractJsonJobHandler, which this module's job handlers extend.
        "jobs :: api",
        // Contribution reminders go out through EmailSenderService.
        "email :: api",
        // DEBT, not a surface. The reminder job resolver implements JobSubject and
        // JobSubjectResolver, which sit under jobs' web package. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "jobs :: legacy-web",
        // Open kernel: ContributionPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
        // Members, memberships and the erasure snapshot are read through UserService,
        // MembershipService and UserErasureService.
        "user :: api",
        // Contribution.user and ContributionReminder.user are owning @ManyToOne
        // associations holding the FKs into users. Also DEBT: the reminder email
        // builder and the bulk use cases read User and Membership rows directly.
        "user :: entities",
    ],
)
class ModuleMetadata
