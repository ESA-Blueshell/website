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
@ApplicationModule(
    id = "sponsor",
    allowedDependencies = [
        // Sponsor.picture is an owning @OneToOne holding the FK into files. Also
        // DEBT: SponsorRepository.findByPicture takes a File, so the query signature
        // reaches for the entity where the id would do.
        "file :: entities",
        // Open kernel: SponsorPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
