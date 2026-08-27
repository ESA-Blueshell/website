package net.blueshell.api.platform.integration.contact

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
@ApplicationModule(id = "contact")
class ModuleMetadata
