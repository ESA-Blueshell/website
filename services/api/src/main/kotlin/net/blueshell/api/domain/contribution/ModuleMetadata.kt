package net.blueshell.api.domain.contribution

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
@ApplicationModule(id = "contribution")
class ModuleMetadata
