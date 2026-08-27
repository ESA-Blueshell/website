package net.blueshell.api.platform.integration.job

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Deferred work: the `JobExecution` record of every attempt, and the Job Manager that lists,
 * filters and retries them. The engine that dispatches and runs a job is the sibling `queue`
 * package, which merges into this module once the packages flatten.
 *
 * A module that owns an entity a payload names implements `JobSubjectResolver` so this module
 * can label an execution without importing that module's services.
 */
@PackageInfo
@ApplicationModule(id = "jobs")
class ModuleMetadata
