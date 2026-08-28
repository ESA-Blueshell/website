package net.blueshell.api.contact

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The contact book behind everything that gets mailed: a `Contact` per person, the lists they
 * belong to, and the id each external system knows them by.
 *
 * Contacts are soft-deleted against a sentinel `deleted_at` rather than removed, so a per-system
 * removal job can still find the external id after the contact is gone from every query.
 */
@PackageInfo
@ApplicationModule(
    id = "contact",
    allowedDependencies = [
        // Open kernel.
        "shared",
        // DEBT. ContactData.toContactData maps a User into the shape pushed to an
        // external system. No contact entity holds an FK into users — Contact
        // stores the id. This wants a contact projection published through
        // user :: api.
        "user :: entities",
    ],
)
class ModuleMetadata
