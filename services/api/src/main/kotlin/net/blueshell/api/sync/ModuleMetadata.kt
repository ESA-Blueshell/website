package net.blueshell.api.sync

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Pushes an aggregate's current state to the external systems that mirror it and remembers the
 * resulting external id in `external_id_mapping`: contacts through contact's `ContactAdapter`,
 * events through event's `CalendarAdapter`.
 *
 * Every push runs as a queued job, retried on the job queue's schedule rather than failing the
 * change that caused it. Calendar and Discord jobs are queued inside that change's transaction;
 * contact jobs once the user change commits, with a daily sweep for each of contacts and calendar
 * catching one whose queueing failed.
 */
@PackageInfo
@ApplicationModule(
    id = "sync",
    allowedDependencies = [
        // AbstractJsonJobHandler, which this module's job handlers extend.
        "jobs :: api",
        // Contacts are pushed through ContactAdapter.
        "contact :: api",
        // Calendar publication runs through EventService and the CalendarAdapter port
        // event declares.
        "event :: api",
        // DEBT. CalendarSyncService reads Event columns to build the calendar
        // payload. No sync entity holds an FK into events. This wants the calendar
        // shape published through event :: api, next to CalendarEventData.
        "event :: entities",
        // Open kernel.
        "shared",
        // Members are resolved through UserService and the fan-out reacts to the user
        // lifecycle events.
        "user :: api",
        // DEBT. SyncAllContactsJob loads whole User rows through UserService.findAll
        // and reads nothing but .id off them. The narrowest reach in the list, and
        // the one with a published replacement already waiting in
        // UserService.findActiveIdsAfter.
        "user :: entities",
    ],
)
class ModuleMetadata
