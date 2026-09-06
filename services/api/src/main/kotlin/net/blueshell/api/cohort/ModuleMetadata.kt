package net.blueshell.api.cohort

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Code-defined audiences — "active members", "paid for this period" — evaluated into `CohortMember`
 * rows and reconciled against the mailing list or group that stands for them in an external system.
 *
 * One `TargetStrategy` per target system stands between the module and that system; the REST
 * surface is the controllers under `web`.
 */
@PackageInfo
@ApplicationModule(
    id = "cohort",
    allowedDependencies = [
        // AbstractJsonJobHandler, which this module's job handlers extend.
        "jobs :: api",
        // The board-members cohort is built from BoardMemberService.
        "board :: api",
        // The committee-members cohort is built from CommitteeService and
        // CommitteeMemberService, and reacts to CommitteeMembershipChanged.
        "committee :: api",
        // DEBT. CommitteeMembersDefinition reads Committee rows to name and
        // populate one cohort per committee. No cohort entity holds an FK into
        // committees — this is a definition reaching into persistence, and it
        // wants a projection published through committee :: api instead.
        "committee :: entities",
        // The list catalogue, its folders and its membership all go through ContactListAdapter.
        "contact :: api",
        // Period cohorts are built from ContributionService and
        // ContributionPeriodService, and react to ContributionChanged.
        "contribution :: api",
        // DEBT. PeriodPayersDefinition and PeriodActiveMembersDefinition read
        // Contribution and ContributionPeriod rows. No cohort entity holds an
        // FK into either table — this wants a projection published through
        // contribution :: api instead.
        "contribution :: entities",
        // The team-roster cohort is built from TeamRosterService.
        "esports :: api",
        // DEBT, not a surface. The cohort job resolvers implement JobSubject and
        // JobSubjectResolver, which sit under jobs' web package. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "jobs :: legacy-web",
        // Open kernel.
        "shared",
        // External ids are resolved through ExternalIdMappingService.
        "sync :: api",
        // DEBT. The membership sync, remediation and inbound reconcile services read
        // ExternalIdMapping rows directly. No cohort entity holds an FK into that
        // table — this wants the mapping shape published through sync :: api.
        "sync :: entities",
        // Members are resolved through UserService and MembershipService, and the
        // engine reacts to the user lifecycle events.
        "user :: api",
        // DEBT. The cohort definitions and the controller response mappers read User
        // rows. No cohort entity holds an FK into users — cohort_member stores the
        // id. This wants a member projection published through user :: api.
        "user :: entities",
    ],
)
class ModuleMetadata
