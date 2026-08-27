package net.blueshell.api.platform.integration.cohort

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Code-defined audiences — "active members", "paid for this period" — evaluated into `CohortMember`
 * rows and reconciled against the mailing list or group that stands for them in an external system.
 *
 * `port/in` is what other modules may call and `port/out` is the driven side, one `CohortPort` per
 * target system; neither is a REST surface, the controllers under `adapter/web` are.
 */
@PackageInfo
@ApplicationModule(id = "cohort")
class ModuleMetadata
