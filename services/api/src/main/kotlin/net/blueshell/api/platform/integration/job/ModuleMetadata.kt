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
@ApplicationModule(
    id = "jobs",
    allowedDependencies = [
        // Open kernel: JobExecutionPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
        // The initiator is resolved through UserService.
        "user :: api",
        // DEBT. JobExecutionSpecifications filters on User columns and
        // JobExecutionViewService reads User rows to name the initiator. No job
        // entity holds an FK into users — job_executions stores the id. This wants
        // an initiator projection published through user :: api.
        "user :: entities",
    ],
)
class ModuleMetadata
