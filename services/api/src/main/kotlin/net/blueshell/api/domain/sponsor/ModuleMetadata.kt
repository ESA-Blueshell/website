package net.blueshell.api.domain.sponsor

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Sponsors listed on the website: name, description and the logo file each one points at.
 *
 * Deliberately thin — a sponsor has no behaviour beyond being shown, so the module is a CRUD
 * surface over one table plus its uniqueness constraints.
 */
@PackageInfo
@ApplicationModule(id = "sponsor")
class ModuleMetadata
