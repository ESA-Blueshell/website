package net.blueshell.api.platform.integration.sync

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Pushes an aggregate's current state to the external systems that mirror it and remembers the
 * resulting external id in `external_id_mapping`. Each destination implements the `SyncTarget`
 * port; the Google Calendar adapter is the sibling `calendar` package, folded in by the flattening.
 *
 * Fan-out runs off `@ApplicationModuleListener`s, so a failed push is retried from the event
 * publication registry instead of failing the transaction that caused it.
 */
@PackageInfo
@ApplicationModule(
    id = "sync",
    allowedDependencies = [
        // Contacts are pushed through ContactAdapter, and the Brevo target composes
        // the Brevo adapter.
        "contact :: api",
        // DEBT. ContactSyncService writes Contact.externalId to keep the legacy
        // list-membership handler working. No sync entity holds an FK into
        // contacts. Removing it is design work, not a rename: contact has to own
        // the write.
        "contact :: entities",
        // DEBT, not a surface. ContactSyncService creates, loads and soft-deletes
        // Contact rows through ContactRepository. Same fix as the entities
        // entry above — contact has to own the write.
        "contact :: legacy-repository",
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
